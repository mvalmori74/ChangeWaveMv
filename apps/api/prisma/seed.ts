import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';
import { loadDotEnv } from '../src/config/load-dotenv.js';

loadDotEnv();

/**
 * Seeds the minimum needed to log in and start a run.
 *
 * It deliberately does *not* seed opportunities, competitors or scores:
 * fabricated market data is exactly what this platform must never produce, and
 * a demo row is indistinguishable from a researched one once it is in the
 * table. Run a research project instead — with LLM_PROVIDER=mock it completes
 * offline and every record it writes is explicitly marked synthetic.
 */
const prisma = new PrismaClient();

async function main(): Promise<void> {
  const email = process.env['SEED_ADMIN_EMAIL'] ?? 'admin@example.com';
  const password = process.env['SEED_ADMIN_PASSWORD'] ?? 'changeme123';

  const user = await prisma.user.upsert({
    where: { email },
    update: {},
    create: {
      email,
      name: 'Administrator',
      passwordHash: await bcrypt.hash(password, 12),
      role: 'ADMIN',
    },
  });

  const existingProject = await prisma.researchProject.findFirst({
    where: { ownerId: user.id, name: 'Automotive maintenance apps (demo)' },
  });

  if (!existingProject) {
    await prisma.researchProject.create({
      data: {
        name: 'Automotive maintenance apps (demo)',
        sector: 'automotive maintenance',
        country: 'Italy',
        platform: 'ANDROID',
        language: 'en',
        timeframe: 'last 12 months',
        objective:
          'Find under-served workflows for independent workshops and mobile mechanics.',
        ownerId: user.id,
      },
    });
  }

  console.log(`Seeded admin ${email} (password: ${password === 'changeme123' ? 'changeme123 - change it' : 'from SEED_ADMIN_PASSWORD'})`);
}

main()
  .catch((error: unknown) => {
    console.error(error);
    process.exit(1);
  })
  .finally(() => {
    void prisma.$disconnect();
  });
