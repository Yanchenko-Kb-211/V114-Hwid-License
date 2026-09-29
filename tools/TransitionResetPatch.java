import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TransitionResetPatch {
    private static final String SNAKE = "com/example/chestdropper/SnakeTransition";
    private static final String AUTO = "com/example/chestdropper/AutoMove";

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

                if (e.getName().equals(SNAKE + ".class")) {
                    data = patchSnakeTransition(data);
                } else if (e.getName().equals(AUTO + ".class")) {
                    data = patchAutoMove(data);
                }

                var outEntry = new java.util.zip.ZipEntry(e.getName());
                outEntry.setTime(e.getTime());
                zout.putNextEntry(outEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }

        System.out.println("Patched restart state and wall-transition guard: " + output);
    }

    private static byte[] patchSnakeTransition(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visitEnd() {
                // Hard guard used by AutoMove when it detects that the player is
                // actually blocked by the wall. The existing startTransition()
                // contains the complete transition sequence.
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "forceTransition",
                        "(Ljava/lang/Object;)V",
                        null,
                        new String[] {"java/lang/Exception"}
                );

                mv.visitCode();
                Label done = new Label();

                mv.visitFieldInsn(Opcodes.GETSTATIC, SNAKE, "phase", "I");
                mv.visitJumpInsn(Opcodes.IFNE, done);

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        SNAKE,
                        "startTransition",
                        "(Ljava/lang/Object;)V",
                        false
                );

                mv.visitLabel(done);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                super.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (!name.equals("tick") || !descriptor.equals("(Ljava/lang/Object;)V")) {
                    return mv;
                }

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();

                        Label skip = new Label();

                        // Reset the transition timing pattern at the start of a
                        // fresh F11 session. This prevents timing state from
                        // leaking from a previous run.
                        visitFieldInsn(Opcodes.GETSTATIC, SNAKE, "phase", "I");
                        visitJumpInsn(Opcodes.IFNE, skip);

                        visitFieldInsn(Opcodes.GETSTATIC, SNAKE, "wasRunning", "Z");
                        visitJumpInsn(Opcodes.IFNE, skip);

                        visitInsn(Opcodes.ICONST_0);
                        visitFieldInsn(Opcodes.PUTSTATIC, SNAKE, "transitionCountPattern", "I");

                        visitLabel(skip);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    private static byte[] patchAutoMove(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (name.equals("tick") && descriptor.equals("(Ljava/lang/Object;)V")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        private boolean injected;

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            super.visitVarInsn(opcode, var);

                            // In the current AutoMove bytecode:
                            //   local 4 = isDropping
                            //   local 5 = target
                            // The target is stored exactly once near the start
                            // of tick(). Put the physical wall detector right
                            // after that state is available.
                            if (!injected && opcode == Opcodes.ASTORE && var == 5) {
                                injected = true;

                                Label continueLabel = new Label();

                                visitVarInsn(Opcodes.ALOAD, 0);
                                visitVarInsn(Opcodes.ILOAD, 4);
                                visitVarInsn(Opcodes.ALOAD, 5);
                                visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        AUTO,
                                        "checkAndForceWallTransition",
                                        "(Ljava/lang/Object;ZLjava/lang/Object;)Z",
                                        false
                                );
                                visitJumpInsn(Opcodes.IFEQ, continueLabel);
                                visitInsn(Opcodes.RETURN);
                                visitLabel(continueLabel);
                            }
                        }
                    };
                }

                if (name.equals("resetRunState") && descriptor.equals("()V")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                        }

                        @Override
                        public void visitEnd() {
                            visitInsn(Opcodes.ICONST_0);
                            visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleValid", "Z");

                            visitInsn(Opcodes.ICONST_0);
                            visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallBlockedTicks", "I");

                            super.visitEnd();
                        }
                    };
                }

                return mv;
            }

            @Override
            public void visitEnd() {
                // State used to detect a real wall block from consecutive D-held
                // ticks. It is deliberately independent from moveTick, because
                // moveTick also drives the normal D pulse pattern.
                super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "wallSampleX",
                        "D",
                        null,
                        null
                ).visitEnd();

                super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "wallSampleZ",
                        "D",
                        null,
                        null
                ).visitEnd();

                super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "wallSampleValid",
                        "Z",
                        null,
                        null
                ).visitEnd();

                super.visitField(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "wallBlockedTicks",
                        "I",
                        null,
                        null
                ).visitEnd();

                addWallDetectorMethod();

                super.visitEnd();
            }

            private void addWallDetectorMethod() {
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "checkAndForceWallTransition",
                        "(Ljava/lang/Object;ZLjava/lang/Object;)Z",
                        null,
                        null
                );

                mv.visitCode();

                Label resetReturn = new Label();
                Label afterEligibility = new Label();
                Label noBlock = new Label();
                Label noTransition = new Label();
                Label done = new Label();

                // Ignore the detector while dropping, while a chest target exists,
                // while movement is not active, during the first few movement ticks,
                // or while a row transition is already running.
                mv.visitVarInsn(Opcodes.ILOAD, 1);
                mv.visitJumpInsn(Opcodes.IFNE, resetReturn);

                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitJumpInsn(Opcodes.IFNONNULL, resetReturn);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "moving", "Z");
                mv.visitJumpInsn(Opcodes.IFEQ, resetReturn);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "moveTick", "I");
                mv.visitIntInsn(Opcodes.BIPUSH, 4);
                mv.visitJumpInsn(Opcodes.IF_ICMPLT, resetReturn);

                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        SNAKE,
                        "isTransitionActive",
                        "()Z",
                        false
                );
                mv.visitJumpInsn(Opcodes.IFNE, resetReturn);

                // player = field(client, "field_1724")
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitLdcInsn("field_1724");
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "field",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                        false
                );
                mv.visitVarInsn(Opcodes.ASTORE, 3);

                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitJumpInsn(Opcodes.IFNULL, resetReturn);

                // x = num(call(player, "method_23317"))
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitLdcInsn("method_23317");
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "call",
                        "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false
                );
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "num",
                        "(Ljava/lang/Object;)D",
                        false
                );
                mv.visitVarInsn(Opcodes.DSTORE, 4);

                // z = num(call(player, "method_23321"))
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitLdcInsn("method_23321");
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "call",
                        "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                        false
                );
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "num",
                        "(Ljava/lang/Object;)D",
                        false
                );
                mv.visitVarInsn(Opcodes.DSTORE, 6);

                // Only a held synthetic D key can confirm that the player is
                // blocked. This avoids interpreting the intentional 5-tick D
                // release window as a wall.
                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "syntheticDPressed", "Z");
                mv.visitJumpInsn(Opcodes.IFEQ, noBlock);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "wallSampleValid", "Z");
                mv.visitJumpInsn(Opcodes.IFEQ, afterEligibility);

                mv.visitVarInsn(Opcodes.DLOAD, 4);
                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "wallSampleX", "D");
                mv.visitInsn(Opcodes.DSUB);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Math",
                        "abs",
                        "(D)D",
                        false
                );
                mv.visitLdcInsn(0.001D);
                mv.visitInsn(Opcodes.DCMPL);
                mv.visitJumpInsn(Opcodes.IFGE, noBlock);

                mv.visitVarInsn(Opcodes.DLOAD, 6);
                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "wallSampleZ", "D");
                mv.visitInsn(Opcodes.DSUB);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Math",
                        "abs",
                        "(D)D",
                        false
                );
                mv.visitLdcInsn(0.001D);
                mv.visitInsn(Opcodes.DCMPL);
                mv.visitJumpInsn(Opcodes.IFGE, noBlock);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "wallBlockedTicks", "I");
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IADD);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallBlockedTicks", "I");
                mv.visitJumpInsn(Opcodes.GOTO, afterEligibility);

                mv.visitLabel(noBlock);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallBlockedTicks", "I");

                mv.visitLabel(afterEligibility);

                // Remember the latest horizontal position for the next held-D tick.
                mv.visitVarInsn(Opcodes.DLOAD, 4);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleX", "D");

                mv.visitVarInsn(Opcodes.DLOAD, 6);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleZ", "D");

                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleValid", "Z");

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "syntheticDPressed", "Z");
                mv.visitJumpInsn(Opcodes.IFEQ, done);

                mv.visitFieldInsn(Opcodes.GETSTATIC, AUTO, "wallBlockedTicks", "I");
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitJumpInsn(Opcodes.IF_ICMPLT, done);

                // Start the full row-transition sequence immediately, before
                // AutoMove can issue another D press on this tick.
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        SNAKE,
                        "forceTransition",
                        "(Ljava/lang/Object;)V",
                        false
                );

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        AUTO,
                        "releaseD",
                        "(Ljava/lang/Object;)V",
                        false
                );

                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "moving", "Z");

                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "moveTick", "I");

                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "scanTick", "I");

                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallBlockedTicks", "I");

                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleValid", "Z");

                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);

                mv.visitLabel(done);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);

                mv.visitLabel(resetReturn);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallBlockedTicks", "I");
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitFieldInsn(Opcodes.PUTSTATIC, AUTO, "wallSampleValid", "Z");
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
