// resizer.java
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;

public class resizer {
    public static void main(String[] args) throws Exception {
        String input = null, output = null;
        int width = 0, height = 0, percent = 0, quality = 90;
        String mode = "fit";
        boolean recursive = false, verbose = false;
        String format = "";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-W") && i+1 < args.length) width = Integer.parseInt(args[++i]);
            else if (args[i].equals("-H") && i+1 < args.length) height = Integer.parseInt(args[++i]);
            else if (args[i].equals("-p") && i+1 < args.length) percent = Integer.parseInt(args[++i]);
            else if (args[i].equals("-m") && i+1 < args.length) mode = args[++i];
            else if (args[i].equals("-q") && i+1 < args.length) quality = Integer.parseInt(args[++i]);
            else if (args[i].equals("-r")) recursive = true;
            else if (args[i].equals("-f") && i+1 < args.length) format = args[++i];
            else if (args[i].equals("-v")) verbose = true;
            else if (args[i].equals("-h") || args[i].equals("--help")) {
                System.out.println("Usage: resizer <input> [output] [options]\n  -W <N>     Width\n  -H <N>     Height\n  -p <N>     Percent\n  -m <mode>  fit|fill|stretch\n  -q <N>     Quality\n  -r         Recursive\n  -f <ext>   Output format\n  -v         Verbose");
                return;
            } else if (input == null) input = args[i];
            else if (output == null) output = args[i];
        }
        if (input == null) { System.err.println("Укажите входной файл или папку."); System.exit(1); }
        if (width == 0 && height == 0 && percent == 0) { System.err.println("Укажите ширину/высоту или процент."); System.exit(1); }
        if (output == null) {
            if (Files.isDirectory(Paths.get(input))) output = input;
            else {
                String ext = format.isEmpty() ? input.substring(input.lastIndexOf('.')) : "." + format;
                output = input.substring(0, input.lastIndexOf('.')) + "_resized" + ext;
            }
        }

        String[] files = getImageFiles(input, recursive);
        if (files.length == 0) { System.out.println("Нет изображений."); System.exit(1); }
        int total = files.length, processed = 0;
        String outFormat = format.isEmpty() ? "jpg" : format;
        for (String f : files) {
            String outFile = output;
            if (Files.isDirectory(Paths.get(output))) {
                Path rel = Paths.get(input).relativize(Paths.get(f));
                Path outDir = Paths.get(output, rel.getParent().toString());
                Files.createDirectories(outDir);
                String base = com.google.common.io.Files.getNameWithoutExtension(f);
                String ext = format.isEmpty() ? com.google.common.io.Files.getFileExtension(f) : format;
                outFile = outDir.resolve(base + "." + ext).toString();
            }
            try {
                BufferedImage img = ImageIO.read(new File(f));
                if (img == null) throw new Exception("Unsupported format");
                BufferedImage resized;
                if (percent > 0) {
                    int newW = img.getWidth() * percent / 100;
                    int newH = img.getHeight() * percent / 100;
                    Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                    resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = resized.createGraphics();
                    g.drawImage(tmp, 0, 0, null);
                    g.dispose();
                } else {
                    int targetW = width, targetH = height;
                    if (mode.equals("fit")) {
                        double ratio = Math.min((double)targetW / img.getWidth(), (double)targetH / img.getHeight());
                        int newW = (int)(img.getWidth() * ratio);
                        int newH = (int)(img.getHeight() * ratio);
                        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                        resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = resized.createGraphics();
                        g.drawImage(tmp, 0, 0, null);
                        g.dispose();
                    } else if (mode.equals("fill")) {
                        double ratio = Math.max((double)targetW / img.getWidth(), (double)targetH / img.getHeight());
                        int newW = (int)(img.getWidth() * ratio);
                        int newH = (int)(img.getHeight() * ratio);
                        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = scaled.createGraphics();
                        g.drawImage(tmp, 0, 0, null);
                        g.dispose();
                        int x = (newW - targetW) / 2;
                        int y = (newH - targetH) / 2;
                        resized = scaled.getSubimage(x, y, targetW, targetH);
                    } else { // stretch
                        Image tmp = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                        resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = resized.createGraphics();
                        g.drawImage(tmp, 0, 0, null);
                        g.dispose();
                    }
                }
                // Конвертация в RGB для JPG
                if (outFormat.equalsIgnoreCase("jpg") || outFormat.equalsIgnoreCase("jpeg")) {
                    BufferedImage rgb = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgb.getGraphics().drawImage(resized, 0, 0, null);
                    resized = rgb;
                }
                ImageIO.write(resized, outFormat, new File(outFile));
                processed++;
                if (verbose) System.out.println("✅ " + f + " -> " + outFile);
                else {
                    int pct = processed * 100 / total;
                    System.out.printf("\r[%s] %d%% %d/%d", "█".repeat(pct/2) + "░".repeat(50-pct/2), pct, processed, total);
                }
            } catch (Exception e) {
                System.err.println("\n❌ Ошибка " + f + ": " + e.getMessage());
            }
        }
        if (!verbose) System.out.println();
        System.out.println("✅ Обработано " + processed + " файлов.");
    }

    static String[] getImageFiles(String path, boolean recursive) {
        Set<String> exts = new HashSet<>(Arrays.asList(".png",".jpg",".jpeg",".bmp",".tiff",".webp"));
        List<String> files = new ArrayList<>();
        Path p = Paths.get(path);
        if (Files.isRegularFile(p)) {
            if (exts.contains(com.google.common.io.Files.getFileExtension(path).toLowerCase()))
                files.add(path);
            return files.toArray(new String[0]);
        }
        if (Files.isDirectory(p)) {
            try {
                Files.walk(p)
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        String ext = com.google.common.io.Files.getFileExtension(f.toString()).toLowerCase();
                        if (exts.contains("." + ext)) files.add(f.toString());
                    });
            } catch (IOException e) {}
        }
        return files.toArray(new String[0]);
    }
}
