// resizer.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.Formats.Jpeg;

class Resizer
{
    static int Main(string[] args)
    {
        string input = null, output = null;
        int width = 0, height = 0, percent = 0, quality = 90;
        string mode = "fit";
        bool recursive = false, verbose = false;
        string format = "";

        for (int i = 0; i < args.Length; i++)
        {
            if (args[i] == "-W" && i+1 < args.Length) width = int.Parse(args[++i]);
            else if (args[i] == "-H" && i+1 < args.Length) height = int.Parse(args[++i]);
            else if (args[i] == "-p" && i+1 < args.Length) percent = int.Parse(args[++i]);
            else if (args[i] == "-m" && i+1 < args.Length) mode = args[++i];
            else if (args[i] == "-q" && i+1 < args.Length) quality = int.Parse(args[++i]);
            else if (args[i] == "-r") recursive = true;
            else if (args[i] == "-f" && i+1 < args.Length) format = args[++i];
            else if (args[i] == "-v") verbose = true;
            else if (args[i] == "-h" || args[i] == "--help")
            {
                Console.WriteLine("Usage: resizer <input> [output] [options]\n  -W <N>     Width\n  -H <N>     Height\n  -p <N>     Percent\n  -m <mode>  fit|fill|stretch\n  -q <N>     Quality\n  -r         Recursive\n  -f <ext>   Output format\n  -v         Verbose");
                return 0;
            }
            else if (input == null) input = args[i];
            else if (output == null) output = args[i];
        }
        if (input == null) { Console.Error.WriteLine("Укажите входной файл или папку."); return 1; }
        if (width == 0 && height == 0 && percent == 0) { Console.Error.WriteLine("Укажите ширину/высоту или процент."); return 1; }
        if (output == null)
        {
            if (Directory.Exists(input)) output = input;
            else
            {
                string ext = string.IsNullOrEmpty(format) ? Path.GetExtension(input) : "." + format;
                output = Path.Combine(Path.GetDirectoryName(input), Path.GetFileNameWithoutExtension(input) + "_resized" + ext);
            }
        }

        var files = GetImageFiles(input, recursive);
        if (files.Count == 0) { Console.WriteLine("Нет изображений."); return 1; }
        int total = files.Count, processed = 0;
        string outFormat = string.IsNullOrEmpty(format) ? "jpg" : format;
        foreach (var f in files)
        {
            string outFile = output;
            if (Directory.Exists(output))
            {
                string rel = Path.GetRelativePath(input, f);
                string outDir = Path.Combine(output, Path.GetDirectoryName(rel));
                Directory.CreateDirectory(outDir);
                string baseName = Path.GetFileNameWithoutExtension(f);
                string ext = string.IsNullOrEmpty(format) ? Path.GetExtension(f).TrimStart('.') : format;
                outFile = Path.Combine(outDir, baseName + "." + ext);
            }
            try
            {
                using var img = Image.Load(f);
                if (percent > 0)
                {
                    int newW = img.Width * percent / 100;
                    int newH = img.Height * percent / 100;
                    img.Mutate(ctx => ctx.Resize(newW, newH));
                }
                else
                {
                    if (mode == "fit")
                        img.Mutate(ctx => ctx.Resize(new ResizeOptions { Size = new Size(width, height), Mode = ResizeMode.Max }));
                    else if (mode == "fill")
                        img.Mutate(ctx => ctx.Resize(new ResizeOptions { Size = new Size(width, height), Mode = ResizeMode.Crop }));
                    else if (mode == "stretch")
                        img.Mutate(ctx => ctx.Resize(width, height));
                }
                var encoder = GetEncoder(outFormat, quality);
                img.Save(outFile, encoder);
                processed++;
                if (verbose) Console.WriteLine($"✅ {f} -> {outFile}");
                else
                {
                    int pct = processed * 100 / total;
                    Console.Write($"\r[{new string('█', pct/2)}{new string('░', 50-pct/2)}] {pct}% {processed}/{total}");
                }
            }
            catch (Exception e)
            {
                Console.WriteLine($"\n❌ Ошибка {f}: {e.Message}");
            }
        }
        if (!verbose) Console.WriteLine();
        Console.WriteLine($"✅ Обработано {processed} файлов.");
        return 0;
    }

    static List<string> GetImageFiles(string path, bool recursive)
    {
        var exts = new HashSet<string>{".png",".jpg",".jpeg",".bmp",".tiff",".webp"};
        var list = new List<string>();
        if (File.Exists(path))
        {
            if (exts.Contains(Path.GetExtension(path).ToLower())) list.Add(path);
            return list;
        }
        if (Directory.Exists(path))
        {
            var options = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;
            foreach (var f in Directory.GetFiles(path, "*.*", options))
                if (exts.Contains(Path.GetExtension(f).ToLower()))
                    list.Add(f);
        }
        return list;
    }

    static IImageEncoder GetEncoder(string format, int quality)
    {
        format = format.ToLower();
        if (format == "jpg" || format == "jpeg")
            return new JpegEncoder { Quality = quality };
        if (format == "png")
            return new SixLabors.ImageSharp.Formats.Png.PngEncoder();
        if (format == "webp")
            return new SixLabors.ImageSharp.Formats.Webp.WebpEncoder { Quality = quality };
        return new JpegEncoder { Quality = quality };
    }
}
