// SPDX-License-Identifier: MPL-2.0
import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;

/** Minimal, checked bytecode adapters; no new Minecraft mixin targets. */
public final class PatchClasses implements Opcodes {
    static final String ROOT="dev/ringworld/";
    static byte[] transform(byte[] original,String name) {
        ClassReader reader=new ClassReader(original); ClassWriter writer=new ClassWriter(0);
        int[] edits={0};
        reader.accept(new ClassVisitor(ASM8,writer) {
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
                MethodVisitor target=super.visitMethod(access,method,desc,signature,exceptions);
                if(name.endsWith("DetailClient.class") && method.equals("rebuild") && desc.equals("(JLdev/ringworld/world/RingGeometry;)V")) {
                    target.visitCode(); target.visitVarInsn(LLOAD,0);target.visitVarInsn(ALOAD,2);
                    target.visitMethodInsn(INVOKESTATIC,ROOT+"detail/VolumeClient","rebuild",desc,false);
                    target.visitInsn(RETURN);target.visitMaxs(3,3);target.visitEnd();edits[0]++;
                    return null;
                }
                return new MethodVisitor(ASM8,target) {
                    @Override public void visitIntInsn(int opcode,int value) {
                        if(name.endsWith("Forge1201AtlasPacket.class") && method.equals("decode") && opcode==SIPUSH && value==1794) {
                            value=24576;edits[0]++;
                        }
                        super.visitIntInsn(opcode,value);
                    }
                    @Override public void visitLdcInsn(Object value) {
                        if(name.endsWith("Forge1201RingNetwork.class") && value.equals("ringworld-atlas-v6-detail-forge1201")) {
                            value="ringworld-atlas-v7-volume4-forge1201";edits[0]++;
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        },0);
        int expected=name.endsWith("Forge1201RingNetwork.class") ? 4 : 1;
        if(edits[0]!=expected)throw new IllegalStateException(name+": expected "+expected+" edits, found "+edits[0]);
        return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[1]);
        try(ZipFile jar=new ZipFile(args[0])) {
            for(String name:new String[]{ROOT+"detail/DetailClient.class",ROOT+"platform/forge1201/Forge1201AtlasPacket.class",ROOT+"platform/forge1201/Forge1201RingNetwork.class"}) {
                byte[] bytes=jar.getInputStream(jar.getEntry(name)).readAllBytes();
                Path path=out.resolve(name);Files.createDirectories(path.getParent());Files.write(path,transform(bytes,name));
            }
        }
    }
}
