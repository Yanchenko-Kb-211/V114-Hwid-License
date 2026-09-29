import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TransitionResetPatch {
    private static final String PKG = "com/example/chestdropper/";
    private static final String FEATURES = PKG + "Features";
    private static final String AUTO = PKG + "AutoMove";
    private static final String SNAKE = PKG + "SnakeTransition";
    private static final String CHEST = PKG + "ChestDropperMod";
    private static final String GUI = PKG + "GuiBridge";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: TransitionResetPatch <input.jar> <output.jar>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        try (java.util.zip.ZipFile zin = new java.util.zip.ZipFile(input.toFile());
             java.util.zip.ZipOutputStream zout =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(output))) {

            var entries = zin.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                byte[] data;
                try (var in = zin.getInputStream(e)) {
                    data = in.readAllBytes();
                }

                switch (e.getName()) {
                    case AUTO + ".class" -> data = addAutoReset(data);
                    case SNAKE + ".class" -> data = addSnakeReset(data);
                    case CHEST + ".class" -> data = addChestReset(data);
                    case GUI + ".class" -> data = addGuiReset(data);
                    case FEATURES + ".class" -> data = patchF11Edge(data);
                    default -> {}
                }

                var outEntry = new java.util.zip.ZipEntry(e.getName());
                outEntry.setTime(e.getTime());
                zout.putNextEntry(outEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }

        System.out.println("Patched: clean automation session on every F11 edge");
    }

    private static byte[] addAutoReset(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "resetSession",
                        "(Ljava/lang/Object;)V",
                        null,
                        null
                );
                mv.visitCode();

                // Release D safely before clearing its cached key state.
                Label relStart = new Label();
                Label relEnd = new Label();
                Label relHandler = new Label();
                Label afterRelease = new Label();
                mv.visitTryCatchBlock(relStart, relEnd, relHandler, "java/lang/Throwable");
                mv.visitLabel(relStart);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AUTO, "releaseD",
                        "(Ljava/lang/Object;)V", false);
                mv.visitLabel(relEnd);
                mv.visitJumpInsn(Opcodes.GOTO, afterRelease);
                mv.visitLabel(relHandler);
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(afterRelease);

                putStaticBoolean(mv, AUTO, "active", false);
                putStaticBoolean(mv, AUTO, "wasBusy", false);
                putStaticBoolean(mv, AUTO, "moving", false);
                putStaticBoolean(mv, AUTO, "syntheticDPressed", false);
                putStaticInt(mv, AUTO, "moveTick", 0);
                putStaticInt(mv, AUTO, "scanTick", 0);
                putStaticNull(mv, AUTO, "currentPos", "Ljava/lang/Object;");
                putStaticNull(mv, AUTO, "rightKey", "Ljava/lang/Object;");
                putStaticNull(mv, AUTO, "setPressed", "Ljava/lang/reflect/Method;");
                putStaticDouble(mv, AUTO, "travelYaw", 0.0D);
                putStaticBoolean(mv, AUTO, "haveTravelYaw", false);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "processed", "Ljava/util/Set;");
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "clear",
                        "()V", true);

                putStaticLong(mv, AUTO, "logicalTick", 0L);
                putStaticBoolean(mv, AUTO, "timingActive", false);
                putStaticInt(mv, AUTO, "timingPhase", -1);
                putStaticLong(mv, AUTO, "nextActionTick", -1L);

                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                super.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] addSnakeReset(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "resetSession",
                        "(Ljava/lang/Object;)V",
                        null,
                        null
                );
                mv.visitCode();

                Label relStart = new Label();
                Label relEnd = new Label();
                Label relHandler = new Label();
                Label afterRelease = new Label();
                mv.visitTryCatchBlock(relStart, relEnd, relHandler, "java/lang/Throwable");
                mv.visitLabel(relStart);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, SNAKE, "safeReleaseHorizontal",
                        "(Ljava/lang/Object;)V", false);
                mv.visitLabel(relEnd);
                mv.visitJumpInsn(Opcodes.GOTO, afterRelease);
                mv.visitLabel(relHandler);
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(afterRelease);

                putStaticInt(mv, SNAKE, "phase", 0);
                putStaticInt(mv, SNAKE, "phaseTicks", 0);
                putStaticBoolean(mv, SNAKE, "movingLeft", false);
                putStaticBoolean(mv, SNAKE, "wasRunning", false);
                putStaticBoolean(mv, SNAKE, "dropStarted", false);
                putStaticLong(mv, SNAKE, "lastErrorLog", 0L);
                putStaticInt(mv, SNAKE, "transitionCountPattern", 0);

                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                super.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] addChestReset(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "resetSession",
                        "()V",
                        null,
                        null
                );
                mv.visitCode();
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CHEST, "reset", "()V", false);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                super.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] addGuiReset(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "resetSession",
                        "(Ljava/lang/Object;)V",
                        null,
                        null
                );
                mv.visitCode();

                // Release a synthetic W so a stopped run cannot leak the forward key
                // into the next F11 session.
                Label relStart = new Label();
                Label relEnd = new Label();
                Label relHandler = new Label();
                Label afterRelease = new Label();
                mv.visitTryCatchBlock(relStart, relEnd, relHandler, "java/lang/Throwable");
                mv.visitLabel(relStart);

                Label noRelease = new Label();
                mv.visitFieldInsn(Opcodes.GETSTATIC, GUI, "syntheticWPressed", "Z");
                mv.visitJumpInsn(Opcodes.IFEQ, noRelease);

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GUI,
                        "resolveForwardKey", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                mv.visitVarInsn(Opcodes.ASTORE, 1);

                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitJumpInsn(Opcodes.IFNULL, noRelease);

                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GUI,
                        "setPressed", "(Ljava/lang/Object;Z)V", false);

                mv.visitLabel(noRelease);
                mv.visitLabel(relEnd);
                mv.visitJumpInsn(Opcodes.GOTO, afterRelease);

                mv.visitLabel(relHandler);
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(afterRelease);

                putStaticBoolean(mv, GUI, "f10Latch", false);
                putStaticBoolean(mv, GUI, "syntheticWPressed", false);
                putStaticNull(mv, GUI, "lastClient", "Ljava/lang/Object;");

                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
                super.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchF11Edge(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (!name.equals("tick") || !descriptor.equals("(Ljava/lang/Object;)V")) {
                    return mv;
                }

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean injected;

                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        super.visitVarInsn(opcode, var);

                        // In the original Features.tick, local 4 is the exact F11
                        // edge flag: f11() && !wasF11. Inject immediately after
                        // that flag is stored, then let the original toggle code
                        // execute unchanged.
                        if (!injected && opcode == Opcodes.ISTORE && var == 4) {
                            injected = true;

                            Label noEdge = new Label();
                            visitVarInsn(Opcodes.ILOAD, 4);
                            visitJumpInsn(Opcodes.IFEQ, noEdge);

                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitMethodInsn(Opcodes.INVOKESTATIC, FEATURES,
                                    "resetAutomationSession", "(Ljava/lang/Object;)V", false);

                            visitLabel(noEdge);
                        }
                    }
                };
            }

            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "resetAutomationSession",
                        "(Ljava/lang/Object;)V",
                        null,
                        null
                );
                mv.visitCode();

                // Do not touch Features.running or Features.wasF11 here.
                // The original F11 edge detector and toggle remain untouched.
                putStaticBoolean(mv, FEATURES, "offShown", false);
                putStaticLong(mv, FEATURES, "lastStatus", 0L);
                putStaticBoolean(mv, FEATURES, "batchStarted", false);
                putStaticNull(mv, FEATURES, "target", "Ljava/lang/Object;");
                putStaticBoolean(mv, FEATURES, "movementMode", false);

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, AUTO,
                        "resetSession", "(Ljava/lang/Object;)V", false);

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, SNAKE,
                        "resetSession", "(Ljava/lang/Object;)V", false);

                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CHEST,
                        "resetSession", "()V", false);

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GUI,
                        "resetSession", "(Ljava/lang/Object;)V", false);

                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                super.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static void putStaticBoolean(MethodVisitor mv, String owner, String field, boolean value) {
        mv.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, "Z");
    }

    private static void putStaticInt(MethodVisitor mv, String owner, String field, int value) {
        if (value == -1) {
            mv.visitInsn(Opcodes.ICONST_M1);
        } else if (value >= 0 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else {
            mv.visitLdcInsn(value);
        }
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, "I");
    }

    private static void putStaticNull(MethodVisitor mv, String owner, String field, String descriptor) {
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, descriptor);
    }

    private static void putStaticDouble(MethodVisitor mv, String owner, String field, double value) {
        mv.visitLdcInsn(value);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, "D");
    }

    private static void putStaticLong(MethodVisitor mv, String owner, String field, long value) {
        mv.visitLdcInsn(value);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, "J");
    }
}
