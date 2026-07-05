# resizer.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import sys
import argparse
from PIL import Image, ImageOps
from pathlib import Path

def get_images(path, recursive=False):
    exts = ('.png', '.jpg', '.jpeg', '.bmp', '.tiff', '.webp')
    files = []
    if os.path.isfile(path):
        if path.lower().endswith(exts):
            files.append(path)
        return files
    if recursive:
        for root, _, filenames in os.walk(path):
            for f in filenames:
                if f.lower().endswith(exts):
                    files.append(os.path.join(root, f))
    else:
        for f in os.listdir(path):
            full = os.path.join(path, f)
            if os.path.isfile(full) and f.lower().endswith(exts):
                files.append(full)
    return files

def resize_image(img, width, height, mode='fit', percent=None):
    if percent:
        w = int(img.width * percent / 100)
        h = int(img.height * percent / 100)
        return img.resize((w, h), Image.Resampling.LANCZOS)
    if mode == 'fit':
        img.thumbnail((width, height), Image.Resampling.LANCZOS)
        return img
    elif mode == 'fill':
        # Обрезаем до пропорций
        target_ratio = width / height
        img_ratio = img.width / img.height
        if img_ratio > target_ratio:
            # Обрезаем по ширине
            new_width = int(img.height * target_ratio)
            offset = (img.width - new_width) // 2
            img = img.crop((offset, 0, offset + new_width, img.height))
        else:
            new_height = int(img.width / target_ratio)
            offset = (img.height - new_height) // 2
            img = img.crop((0, offset, img.width, offset + new_height))
        return img.resize((width, height), Image.Resampling.LANCZOS)
    elif mode == 'stretch':
        return img.resize((width, height), Image.Resampling.LANCZOS)
    else:
        raise ValueError(f"Неизвестный режим: {mode}")

def process_image(input_path, output_path, width, height, mode, quality, percent, output_format):
    img = Image.open(input_path)
    # Конвертация в RGB для JPG
    if output_format.lower() in ('jpg', 'jpeg'):
        if img.mode in ('RGBA', 'LA', 'P'):
            img = img.convert('RGB')
    img = resize_image(img, width, height, mode, percent)
    save_kwargs = {'quality': quality, 'optimize': True} if output_format.lower() in ('jpg', 'jpeg') else {}
    img.save(output_path, format=output_format.upper(), **save_kwargs)

def main():
    parser = argparse.ArgumentParser(description="Image Resizer")
    parser.add_argument('input', help='Входной файл или папка')
    parser.add_argument('output', nargs='?', help='Выходной файл или папка')
    parser.add_argument('-W', '--width', type=int, help='Ширина в пикселях')
    parser.add_argument('-H', '--height', type=int, help='Высота в пикселях')
    parser.add_argument('-p', '--percent', type=int, help='Процент от исходного размера')
    parser.add_argument('-m', '--mode', choices=['fit', 'fill', 'stretch'], default='fit', help='Режим изменения')
    parser.add_argument('-q', '--quality', type=int, default=90, help='Качество JPG/WEBP')
    parser.add_argument('-r', '--recursive', action='store_true', help='Рекурсивный обход')
    parser.add_argument('-f', '--format', help='Выходной формат')
    parser.add_argument('-v', '--verbose', action='store_true', help='Подробный вывод')
    args = parser.parse_args()

    if not args.width and not args.height and not args.percent:
        print("Укажите ширину/высоту или процент.", file=sys.stderr)
        sys.exit(1)

    input_path = args.input
    output_path = args.output
    if not output_path:
        if os.path.isdir(input_path):
            output_path = input_path
        else:
            base = os.path.splitext(input_path)[0]
            ext = args.format or os.path.splitext(input_path)[1][1:]
            output_path = f"{base}_resized.{ext}"

    files = get_images(input_path, args.recursive)
    if not files:
        print("Нет изображений для обработки.", file=sys.stderr)
        sys.exit(1)

    total = len(files)
    processed = 0
    out_format = args.format or 'jpg'
    for f in files:
        rel = os.path.relpath(f, input_path) if os.path.isdir(input_path) else os.path.basename(f)
        out_dir = output_path if os.path.isdir(output_path) else os.path.dirname(output_path)
        if os.path.isdir(input_path) and os.path.isdir(output_path):
            out_file = os.path.join(output_path, rel)
            out_dir = os.path.dirname(out_file)
        else:
            out_file = output_path
        os.makedirs(out_dir, exist_ok=True)
        if os.path.isdir(output_path):
            base = os.path.splitext(os.path.basename(f))[0]
            ext = args.format or os.path.splitext(f)[1][1:]
            out_file = os.path.join(output_path, f"{base}.{ext}")
            if args.recursive:
                rel_dir = os.path.relpath(os.path.dirname(f), input_path)
                if rel_dir != '.':
                    out_dir = os.path.join(output_path, rel_dir)
                    os.makedirs(out_dir, exist_ok=True)
                    out_file = os.path.join(out_dir, f"{base}.{ext}")
        try:
            process_image(f, out_file, args.width, args.height, args.mode, args.quality, args.percent, out_format)
            processed += 1
            if args.verbose:
                print(f"✅ {f} -> {out_file}")
            else:
                pct = int(processed / total * 100)
                bar = '█' * (pct // 2) + '░' * (50 - pct // 2)
                print(f"\r[{bar}] {pct}% {processed}/{total}", end='', flush=True)
        except Exception as e:
            print(f"\n❌ Ошибка {f}: {e}", file=sys.stderr)
    if not args.verbose:
        print()
    print(f"✅ Обработано {processed} файлов.")

if __name__ == '__main__':
    main()
