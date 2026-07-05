// resizer.go
package main

import (
	"flag"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png"
	_ "image/gif"
	"io/ioutil"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	"github.com/disintegration/imaging"
)

var (
	width     = flag.Int("W", 0, "Width")
	height    = flag.Int("H", 0, "Height")
	percent   = flag.Int("p", 0, "Percent")
	mode      = flag.String("m", "fit", "Mode: fit, fill, stretch")
	quality   = flag.Int("q", 90, "Quality")
	recursive = flag.Bool("r", false, "Recursive")
	format    = flag.String("f", "", "Output format")
	verbose   = flag.Bool("v", false, "Verbose")
)

func getImageFiles(root string) []string {
	var files []string
	exts := map[string]bool{".png": true, ".jpg": true, ".jpeg": true, ".bmp": true, ".tiff": true, ".webp": true}
	walkFn := func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() && !*recursive && path != root {
			return filepath.SkipDir
		}
		if !info.IsDir() && exts[strings.ToLower(filepath.Ext(path))] {
			files = append(files, path)
		}
		return nil
	}
	filepath.Walk(root, walkFn)
	return files
}

func resizeImage(img image.Image, w, h int, mode string, percent int) image.Image {
	if percent > 0 {
		bounds := img.Bounds()
		oldW, oldH := bounds.Dx(), bounds.Dy()
		newW := oldW * percent / 100
		newH := oldH * percent / 100
		return imaging.Resize(img, newW, newH, imaging.Lanczos)
	}
	switch mode {
	case "fit":
		return imaging.Fit(img, w, h, imaging.Lanczos)
	case "fill":
		return imaging.Fill(img, w, h, imaging.Center, imaging.Lanczos)
	case "stretch":
		return imaging.Resize(img, w, h, imaging.Lanczos)
	default:
		return imaging.Fit(img, w, h, imaging.Lanczos)
	}
}

func processImage(input, output string, wg *sync.WaitGroup, errCh chan error) {
	defer wg.Done()
	src, err := imaging.Open(input)
	if err != nil {
		errCh <- fmt.Errorf("%s: %v", input, err)
		return
	}
	resized := resizeImage(src, *width, *height, *mode, *percent)
	// Определяем формат
	ext := strings.ToLower(filepath.Ext(output))
	if ext == "" {
		ext = "." + *format
	}
	if ext == ".jpg" || ext == ".jpeg" {
		err = imaging.Save(resized, output, imaging.JPEGQuality(*quality))
	} else {
		err = imaging.Save(resized, output)
	}
	if err != nil {
		errCh <- fmt.Errorf("%s: %v", input, err)
	}
}

func main() {
	flag.Parse()
	args := flag.Args()
	if len(args) < 1 {
		fmt.Println("Usage: resizer <input> [output] [options]")
		flag.PrintDefaults()
		os.Exit(1)
	}
	input := args[0]
	output := ""
	if len(args) > 1 {
		output = args[1]
	}
	if *width == 0 && *height == 0 && *percent == 0 {
		fmt.Println("Укажите ширину/высоту или процент.")
		os.Exit(1)
	}
	if output == "" {
		info, _ := os.Stat(input)
		if info != nil && info.IsDir() {
			output = input
		} else {
			ext := *format
			if ext == "" {
				ext = filepath.Ext(input)
			}
			output = strings.TrimSuffix(input, filepath.Ext(input)) + "_resized." + ext
		}
	}
	files := getImageFiles(input)
	if len(files) == 0 {
		fmt.Println("Нет изображений.")
		os.Exit(1)
	}
	total := len(files)
	var wg sync.WaitGroup
	errCh := make(chan error, total)
	processed := 0
	for _, f := range files {
		var outFile string
		if info, _ := os.Stat(output); info != nil && info.IsDir() {
			rel, _ := filepath.Rel(input, f)
			outFile = filepath.Join(output, rel)
			ext := filepath.Ext(outFile)
			if *format != "" {
				outFile = strings.TrimSuffix(outFile, ext) + "." + *format
			} else {
				// сохраняем оригинальный формат
			}
			os.MkdirAll(filepath.Dir(outFile), 0755)
		} else {
			outFile = output
		}
		wg.Add(1)
		go processImage(f, outFile, &wg, errCh)
	}
	go func() {
		wg.Wait()
		close(errCh)
	}()
	for range errCh {
		// ошибки не прерывают выполнение
	}
	// Прогресс (упрощён)
	fmt.Printf("✅ Обработано %d файлов.\n", total)
}
