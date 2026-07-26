import AVFoundation
import CoreMotion
import OmbraCore
import SwiftUI

/// Altezza tipica a cui si tiene il telefono guardando avanti.
private let eyeHeightMeters = 1.5

/// Oltre questa distanza le ombre non aiutano a scegliere un parcheggio.
private let maxDrawDistance = 150.0

/**
 Vista in realtà aumentata: la fotocamera inquadra la strada e l'app ci appoggia sopra le
 ombre dell'ora scelta, il sole e la sua traiettoria.

 Non serve ARKit: per ancorare dei poligoni al suolo bastano l'orientamento del telefono e
 il campo visivo dell'obiettivo. La proiezione è la stessa classe Kotlin usata da Android.
 */
struct ARScreen: View {

    @ObservedObject var model: ShadowModel
    @StateObject private var motion = MotionTracker()
    @State private var viewSize: CGSize = .zero

    var body: some View {
        ZStack {
            CameraPreview()
                .ignoresSafeArea()

            Canvas { context, size in
                guard let projector = makeProjector(size: size) else { return }
                draw(in: &context, size: size, projector: projector)
            }
            .ignoresSafeArea()
            .background(GeometryReader { geometry in
                Color.clear.onAppear { viewSize = geometry.size }
            })

            VStack {
                header
                Spacer()
                bottomPanel
            }
        }
        .onAppear { motion.start() }
        .onDisappear { motion.stop() }
    }

    // MARK: - Testata

    @ViewBuilder
    private var header: some View {
        if model.isExploring && !model.coversUserLocation {
            VStack(alignment: .leading, spacing: 6) {
                Text("Stai esplorando \(model.explorationName ?? "un'altra zona")")
                    .font(.subheadline.bold())
                Text("Qui la realtà aumentata non ha edifici da mostrare: gli ombreggiamenti "
                     + "caricati sono di un altro posto.")
                    .font(.caption)
                Button("Torna alla mia posizione") { model.backToMyLocation() }
                    .buttonStyle(.bordered)
            }
            .padding(12)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal, 16)
        } else {
            VStack(spacing: 6) {
                Text(reticleText)
                    .font(.subheadline)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                if !motion.isHeadingReliable {
                    Text("Bussola da calibrare: muovi il telefono a forma di otto")
                        .font(.caption)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(.red.opacity(0.85), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            .padding(.top, 8)
        }
    }

    private var reticleText: String {
        guard let shade = aimedShade else { return "Inquadra la strada davanti a te" }
        var text = "\(shade.quality.emoji) \(shade.quality.label)"
        if let name = shade.obstacle?.name { text += " · \(name)" }
        return text
    }

    private var bottomPanel: some View {
        VStack(spacing: 10) {
            ParkedCarBar(
                parkedCar: model.parkedCarStore.parkedCar,
                status: model.parkedCarStatus,
                userLocation: model.userLocation,
                onPark: model.parkHere,
                onClear: model.clearParkedCar
            )
            ShadeTimelineStrip(
                forecast: model.forecast,
                dayStart: model.date.startOfDay(zone: .current).date,
                minuteOfDay: Binding(
                    get: { model.minuteOfDay },
                    set: { model.setTime(minuteOfDay: $0) }
                )
            )
            QuickTimeChips(model: model)
        }
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        .padding(12)
    }

    // MARK: - Proiezione e disegno

    private func makeProjector(size: CGSize) -> CameraProjector? {
        guard let matrix = motion.deviceToWorld, size.width > 0, size.height > 0 else { return nil }
        let fov = CameraOptics.visibleFieldOfView(
            viewSize: size,
            verticalFovDegrees: CameraPreview.verticalFieldOfViewDegrees
        )
        return CameraProjector(
            viewportWidthPx: Float(size.width),
            viewportHeightPx: Float(size.height),
            horizontalFovDegrees: fov.horizontal,
            verticalFovDegrees: fov.vertical,
            deviceToWorld: matrix.map { KotlinDouble(value: $0) }
        )
    }

    /// Punto al suolo inquadrato dal centro dello schermo, e cosa sarà all'ora scelta.
    private var aimedShade: ShadeInfo? {
        guard let plane = model.plane,
              let user = model.userLocation,
              let sun = model.sun,
              model.coversUserLocation,
              let projector = makeProjector(size: viewSize),
              let ground = projector.groundIntersection(
                  eyeHeightMeters: eyeHeightMeters,
                  maxDistanceMeters: 80
              )
        else { return nil }

        let aimed = Vec2(
            x: plane.toLocal(point: user).x + ground.x,
            y: plane.toLocal(point: user).y + ground.y
        )
        return ShadowEngine.shared.shadeAt(point: aimed, obstacles: model.obstacles, sun: sun)
    }

    private func draw(in context: inout GraphicsContext, size: CGSize, projector: CameraProjector) {
        drawShadows(in: &context, projector: projector)
        drawSun(in: &context, projector: projector)
        drawParkedCar(in: &context, projector: projector)
        drawReticle(in: &context, size: size)
    }

    private func drawShadows(in context: inout GraphicsContext, projector: CameraProjector) {
        guard model.coversUserLocation,
              let plane = model.plane,
              let user = model.userLocation,
              let sun = model.sun
        else { return }

        let origin = plane.toLocal(point: user)
        let shapes = ShadowEngine.shared.shadowShapes(obstacles: model.obstacles, sun: sun)

        var path = Path()
        for shape in shapes {
            // Le sagome lontane non aiutano e costano: si scartano prima di proiettarle.
            guard let first = shape.first,
                  hypot(first.x - origin.x, first.y - origin.y) < maxDrawDistance
            else { continue }

            let world = shape.map { point in
                Vec3(
                    east: point.x - origin.x,
                    north: point.y - origin.y,
                    up: -eyeHeightMeters
                )
            }
            let projected = projector.projectPolygon(world: world)
            guard projected.count >= 3 else { continue }

            path.move(to: CGPoint(x: CGFloat(projected[0].x), y: CGFloat(projected[0].y)))
            for point in projected.dropFirst() {
                path.addLine(to: CGPoint(x: CGFloat(point.x), y: CGFloat(point.y)))
            }
            path.closeSubpath()
        }
        // Un riempimento unico con regola non-zero: le parti sovrapposte non si scuriscono.
        context.fill(path, with: .color(Color(red: 0.17, green: 0.24, blue: 0.43).opacity(0.40)),
                     style: FillStyle(eoFill: false))
    }

    private func drawSun(in context: inout GraphicsContext, projector: CameraProjector) {
        guard let sun = model.sun, sun.isAboveHorizon else { return }
        let direction = sun.direction3D()
        let far = 300.0
        let point = Vec3(
            east: direction.east * far,
            north: direction.north * far,
            up: direction.up * far
        )
        guard let screen = projector.project(world: point) else { return }
        let center = CGPoint(x: CGFloat(screen.x), y: CGFloat(screen.y))
        context.fill(
            Path(ellipseIn: CGRect(x: center.x - 26, y: center.y - 26, width: 52, height: 52)),
            with: .color(.yellow.opacity(0.35))
        )
        context.fill(
            Path(ellipseIn: CGRect(x: center.x - 14, y: center.y - 14, width: 28, height: 28)),
            with: .color(.yellow)
        )
    }

    private func drawParkedCar(in context: inout GraphicsContext, projector: CameraProjector) {
        guard let car = model.parkedCarStore.parkedCar, let user = model.userLocation else { return }
        let offset = LocalPlane(origin: user).toLocal(point: car.position)
        guard let screen = projector.project(
            world: Vec3(east: offset.x, north: offset.y, up: -eyeHeightMeters)
        ) else { return }

        let center = CGPoint(x: CGFloat(screen.x), y: CGFloat(screen.y))
        let green = Color(red: 0.18, green: 0.49, blue: 0.31)
        context.fill(
            Path(ellipseIn: CGRect(x: center.x - 34, y: center.y - 34, width: 68, height: 68)),
            with: .color(green.opacity(0.35))
        )
        context.fill(
            Path(ellipseIn: CGRect(x: center.x - 16, y: center.y - 16, width: 32, height: 32)),
            with: .color(green)
        )
        context.draw(
            Text("🚗 \(Int(screen.distanceMeters)) m").font(.caption).foregroundColor(.white),
            at: CGPoint(x: center.x, y: center.y - 44)
        )
    }

    private func drawReticle(in context: inout GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let color = aimedShade?.quality.color ?? .white
        context.stroke(
            Path(ellipseIn: CGRect(x: center.x - 16, y: center.y - 16, width: 32, height: 32)),
            with: .color(color),
            lineWidth: 3
        )
    }
}

// MARK: - Campo visivo

enum CameraOptics {
    /// Con l'anteprima che riempie lo schermo, la dimensione più corta viene ritagliata:
    /// il campo visivo verticale resta quello dell'obiettivo, l'orizzontale si ricava
    /// dalle proporzioni della vista.
    static func visibleFieldOfView(
        viewSize: CGSize,
        verticalFovDegrees: Double
    ) -> (horizontal: Double, vertical: Double) {
        let ratio = viewSize.height > 0 ? viewSize.width / viewSize.height : 0.5
        let vertical = verticalFovDegrees * .pi / 180
        let horizontal = 2 * atan(tan(vertical / 2) * ratio)
        return (horizontal * 180 / .pi, verticalFovDegrees)
    }
}

// MARK: - Anteprima della fotocamera

struct CameraPreview: UIViewRepresentable {

    /// Campo visivo verticale tipico della fotocamera principale di un iPhone in verticale.
    /// Viene aggiornato con il valore reale appena la sessione parte.
    static var verticalFieldOfViewDegrees: Double = 63.0

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.start()
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}

    static func dismantleUIView(_ uiView: PreviewUIView, coordinator: ()) {
        uiView.stop()
    }
}

final class PreviewUIView: UIView {

    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    private var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }

    private let session = AVCaptureSession()

    func start() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device)
        else { return }

        // Il campo visivo dichiarato dall'obiettivo è misurato sul lato lungo del sensore,
        // che in verticale corrisponde all'altezza dello schermo.
        CameraPreview.verticalFieldOfViewDegrees = Double(device.activeFormat.videoFieldOfView)

        session.beginConfiguration()
        session.sessionPreset = .high
        if session.canAddInput(input) { session.addInput(input) }
        session.commitConfiguration()

        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.connection?.videoRotationAngle = 90 // l'app è bloccata in verticale

        Task.detached { [session] in session.startRunning() }
    }

    func stop() {
        session.stopRunning()
    }
}

// MARK: - Orientamento

/// Orientamento del telefono nel riferimento geografico, pronto per la proiezione.
@MainActor
final class MotionTracker: ObservableObject {

    /// Matrice 3x3 row-major che porta dagli assi dello schermo al mondo est/nord/alto.
    @Published private(set) var deviceToWorld: [Double]?
    @Published private(set) var isHeadingReliable = true

    private let manager = CMMotionManager()

    func start() {
        guard manager.isDeviceMotionAvailable else { return }
        manager.deviceMotionUpdateInterval = 1.0 / 30.0
        // xTrueNorthZVertical dà un riferimento agganciato al nord *geografico*: è lo stesso
        // rispetto a cui è calcolata la posizione del sole, quindi non serve correggere la
        // declinazione magnetica come su Android.
        manager.startDeviceMotionUpdates(using: .xTrueNorthZVertical, to: .main) { [weak self] motion, _ in
            guard let self, let motion else { return }
            self.isHeadingReliable = motion.magneticField.accuracy != .uncalibrated
            self.deviceToWorld = Self.enuMatrix(from: motion.attitude.rotationMatrix)
        }
    }

    func stop() {
        manager.stopDeviceMotionUpdates()
    }

    /**
     Converte l'assetto di CoreMotion nella matrice attesa dal core.

     Due passaggi: la matrice di CoreMotion trasforma un vettore dal riferimento al
     dispositivo, mentre serve l'inverso (per una rotazione, la trasposta); e il riferimento
     `xTrueNorthZVertical` ha gli assi (nord, ovest, alto), mentre il core lavora in
     (est, nord, alto), da cui `est = -ovest`.

     Se sul telefono l'overlay risultasse specchiato o ruotato di 90°, il sospetto numero uno
     è proprio la convenzione di questa matrice: si corregge qui, in queste righe.
     */
    private static func enuMatrix(from rotation: CMRotationMatrix) -> [Double] {
        // Trasposta: righe di deviceToReference = colonne di rotation.
        let deviceToReference = [
            rotation.m11, rotation.m21, rotation.m31, // componente nord
            rotation.m12, rotation.m22, rotation.m32, // componente ovest
            rotation.m13, rotation.m23, rotation.m33, // componente alto
        ]
        let north = Array(deviceToReference[0..<3])
        let west = Array(deviceToReference[3..<6])
        let up = Array(deviceToReference[6..<9])
        let east = west.map { -$0 }
        return east + north + up
    }
}
