import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TransitionResetPatch {
    private static final String TARGET = "com/example/chestdropper/SnakeTransition";
    private static final String FIELD = "transitionCountPattern";
    private static final String DESC = "I";

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

                if (e.getName().equals(TARGET + ".class")) {
                    data = patchClass(data);
                }

                var outEntry = new java.util.zip.ZipEntry(e.getName());
                outEntry.setTime(e.getTime());
                zout.putNextEntry(outEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }

        System.out.println("Patched: " + output);
    }

    private static byte[] patchClass(byte[] original) {
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
                    @Override
                    public void visitCode() {
                        super.visitCode();

                        Label skip = new Label();

                        // Reset the transition pattern only at the start of a fresh
                        // run (phase == 0 && wasRunning == false). This prevents
                        // timing state from leaking from one F11 session into another.
                        visitFieldInsn(Opcodes.GETSTATIC, TARGET, "phase", "I");
                        visitJumpInsn(Opcodes.IFNE, skip);

                        visitFieldInsn(Opcodes.GETSTATIC, TARGET, "wasRunning", "Z");
                        visitJumpInsn(Opcodes.IFNE, skip);

                        visitInsn(Opcodes.ICONST_0);
                        visitFieldInsn(Opcodes.PUTSTATIC, TARGET, FIELD, DESC);

                        visitLabel(skip);
                    }
                };
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }
}
