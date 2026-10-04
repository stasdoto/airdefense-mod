import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Dev tooling only: turns the compile classpath into signature-only stubs (no method bodies, no private members,
 * no resources) with class version 65, so the mod can be type-checked with a plain JDK 21 javac outside CI.
 * Usage: java Stubber.java outDir jar...
 */
public class Stubber {
	public static void main(String[] args) throws IOException {
		Path out = Path.of(args[0]);
		Files.createDirectories(out);
		ClassFile cf = ClassFile.of(ClassFile.ConstantPoolSharingOption.NEW_POOL);
		int classes = 0;
		int failed = 0;
		for (int i = 1; i < args.length; i++) {
			Path jar = Path.of(args[i]);
			Path target = out.resolve(jar.getFileName().toString());
			try (ZipFile zf = new ZipFile(jar.toFile()); ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
				Enumeration<? extends ZipEntry> en = zf.entries();
				while (en.hasMoreElements()) {
					ZipEntry e = en.nextElement();
					String name = e.getName();
					if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) {
						continue;
					}
					byte[] bytes;
					try (InputStream in = zf.getInputStream(e)) {
						bytes = in.readAllBytes();
					}
					byte[] stub;
					try {
						stub = stub(cf, bytes);
					} catch (Throwable t) {
						failed++;
						if (failed < 20) {
							System.out.println("skip " + name + ": " + t);
						}
						continue;
					}
					zos.putNextEntry(new ZipEntry(name));
					zos.write(stub);
					zos.closeEntry();
					classes++;
				}
			}
		}
		System.out.println("stubbed " + classes + " classes, failed " + failed);
	}

	static byte[] stub(ClassFile cf, byte[] bytes) {
		ClassModel cm = cf.parse(bytes);
		ClassTransform t = (cb, el) -> {
			switch (el) {
				case java.lang.classfile.ClassFileVersion v -> cb.withVersion(65, 0);
				case MethodModel m -> {
					if (m.flags().has(AccessFlag.PRIVATE) || m.flags().has(AccessFlag.SYNTHETIC)) {
						return;
					}
					cb.withMethod(m.methodName(), m.methodType(), m.flags().flagsMask(), mb -> {
						for (MethodElement me : m) {
							if (!(me instanceof CodeModel)) {
								mb.with(me);
							}
						}
						if (!m.flags().has(AccessFlag.ABSTRACT) && !m.flags().has(AccessFlag.NATIVE)) {
							mb.withCode(code -> code.aconst_null().athrow());
						}
					});
				}
				case FieldModel f -> {
					if (!f.flags().has(AccessFlag.PRIVATE)) {
						cb.with(f);
					}
				}
				case SourceFileAttribute s -> {
				}
				default -> cb.with(el);
			}
		};
		return cf.transformClass(cm, t);
	}
}
