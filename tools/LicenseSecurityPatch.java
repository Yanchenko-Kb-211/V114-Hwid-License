import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class LicenseSecurityPatch {
    private static final String LM = "com/example/chestdropper/LicenseManager";
    private static final String PROOF = "com/example/chestdropper/LicenseProofClient";
    private static final String ATOMIC = "java/util/concurrent/atomic/AtomicBoolean";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: LicenseSecurityPatch <input.jar> <output.jar> <LicenseProofClient.class>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        byte[] proofClass = Files.readAllBytes(Path.of(args[2]));

        try (JarFile jar = new JarFile(input.toFile());
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(output))) {

            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                byte[] data = jar.getInputStream(e).readAllBytes();

                if (e.getName().equals(LM + ".class")) {
                    data = patchLicenseManager(data);
                }

                JarEntry ne = new JarEntry(e.getName());
                ne.setTime(e.getTime());
                out.putNextEntry(ne);
                out.write(data);
                out.closeEntry();
            }

            JarEntry proof = new JarEntry(PROOF + ".class");
            out.putNextEntry(proof);
            out.write(proofClass);
            out.closeEntry();
        }

        System.out.println("Signed HWID proof integrated.");
    }

    private static byte[] patchLicenseManager(byte[] original) {
        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] exceptions) {

                if (name.equals("checkServer") &&
                    desc.equals("(Ljava/lang/String;Ljava/lang/String;)Z")) {

                    MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
                    mv.visitCode();

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            PROOF,
                            "check",
                            "(Ljava/lang/String;Ljava/lang/String;)Z",
                            false
                    );
                    mv.visitInsn(Opcodes.IRETURN);

                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    return null;
                }

                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);

                if (!name.equals("tick") || !desc.equals("(Ljava/lang/Object;)V")) {
                    return mv;
                }

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private boolean afterAuthorizedGet;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);

                        if (opcode == Opcodes.INVOKEVIRTUAL
                                && owner.equals(ATOMIC)
                                && method.equals("get")
                                && descriptor.equals("()Z")) {
                            afterAuthorizedGet = true;
                        }
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                        if (afterAuthorizedGet && opcode == Opcodes.IFEQ) {
                            afterAuthorizedGet = false;

                            // Keep the original unauthorized branch.
                            super.visitJumpInsn(Opcodes.IFEQ, label);

                            Label validProof = new Label();

                            visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    PROOF,
                                    "isValid",
                                    "()Z",
                                    false
                            );
                            super.visitJumpInsn(Opcodes.IFNE, validProof);

                            // If the old boolean says authorized but the signed
                            // proof is missing/invalid, force the old flag back to
                            // false and continue through the existing fail-closed path.
                            visitFieldInsn(
                                    Opcodes.GETSTATIC,
                                    LM,
                                    "authorized",
                                    "Ljava/util/concurrent/atomic/AtomicBoolean;"
                            );
                            visitInsn(Opcodes.ICONST_0);
                            visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL,
                                    ATOMIC,
                                    "set",
                                    "(Z)V",
                                    false
                            );
                            super.visitJumpInsn(Opcodes.GOTO, label);

                            super.visitLabel(validProof);
                            visitInsn(Opcodes.RETURN);
                            return;
                        }

                        super.visitJumpInsn(opcode, label);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
