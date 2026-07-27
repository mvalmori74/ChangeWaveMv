import type { PrismaClient, User } from '@prisma/client';
import bcrypt from 'bcryptjs';
import type { AuthResponse, LoginRequest, RegisterRequest } from '@aiaf/shared';
import { ConflictError, UnauthorizedError } from '../core/errors.js';

export type TokenSigner = (payload: { sub: string; email: string; role: string }) => string;

const BCRYPT_ROUNDS = 12;

export class AuthService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly signToken: TokenSigner,
  ) {}

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const email = request.email.toLowerCase().trim();
    const existing = await this.prisma.user.findUnique({ where: { email } });
    if (existing) throw new ConflictError('An account with this email already exists');

    // The first account to register owns the instance; everyone after is an
    // analyst until an admin promotes them.
    const isFirstUser = (await this.prisma.user.count()) === 0;

    const user = await this.prisma.user.create({
      data: {
        email,
        name: request.name,
        passwordHash: await bcrypt.hash(request.password, BCRYPT_ROUNDS),
        role: isFirstUser ? 'ADMIN' : 'ANALYST',
      },
    });

    return this.toAuthResponse(user);
  }

  async login(request: LoginRequest): Promise<AuthResponse> {
    const email = request.email.toLowerCase().trim();
    const user = await this.prisma.user.findUnique({ where: { email } });

    // Compare against a dummy hash when the user is unknown so that a missing
    // account and a wrong password take the same time to answer.
    const hash = user?.passwordHash ?? '$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinv';
    const valid = await bcrypt.compare(request.password, hash);
    if (!user || !valid) throw new UnauthorizedError('Invalid email or password');

    return this.toAuthResponse(user);
  }

  async ensureBootstrapAdmin(email: string, password: string): Promise<void> {
    const normalised = email.toLowerCase().trim();
    const existing = await this.prisma.user.findUnique({ where: { email: normalised } });
    if (existing) return;

    await this.prisma.user.create({
      data: {
        email: normalised,
        name: 'Administrator',
        passwordHash: await bcrypt.hash(password, BCRYPT_ROUNDS),
        role: 'ADMIN',
      },
    });
  }

  private toAuthResponse(user: User): AuthResponse {
    return {
      token: this.signToken({ sub: user.id, email: user.email, role: user.role }),
      user: { id: user.id, email: user.email, name: user.name, role: user.role },
    };
  }
}
