#!/usr/bin/env ruby
# resizer.rb
# encoding: UTF-8

require 'rmagick'
include Magick
require 'optparse'
require 'fileutils'

options = { width: 0, height: 0, percent: 0, mode: 'fit', quality: 90, recursive: false, format: nil, verbose: false }
input = output = nil

OptionParser.new do |opts|
  opts.banner = "Usage: resizer.rb <input> [output] [options]"
  opts.on('-W N', Integer, 'Width') { |v| options[:width] = v }
  opts.on('-H N', Integer, 'Height') { |v| options[:height] = v }
  opts.on('-p N', Integer, 'Percent') { |v| options[:percent] = v }
  opts.on('-m MODE', 'fit|fill|stretch') { |v| options[:mode] = v }
  opts.on('-q N', Integer, 'Quality') { |v| options[:quality] = v }
  opts.on('-r', 'Recursive') { options[:recursive] = true }
  opts.on('-f EXT', 'Format') { |v| options[:format] = v }
  opts.on('-v', 'Verbose') { options[:verbose] = true }
  opts.on('-h', 'Help') { puts opts; exit }
end.parse!

ARGV.each_with_index do |arg, i|
  input ||= arg
  output ||= arg if i > 0
end

unless input
  puts "Укажите входной файл или папку."
  exit 1
end
if options[:width] == 0 && options[:height] == 0 && options[:percent] == 0
  puts "Укажите ширину/высоту или процент."
  exit 1
end
if output.nil?
  if File.directory?(input)
    output = input
  else
    ext = options[:format] || File.extname(input)
    output = File.dirname(input) + '/' + File.basename(input, '.*') + '_resized' + ext
  end
end

def get_files(path, recursive)
  exts = %w[.png .jpg .jpeg .bmp .tiff .webp]
  return [path] if File.file?(path) && exts.include?(File.extname(path).downcase)
  pattern = recursive ? '**/*' : '*'
  Dir.glob(File.join(path, pattern)).select { |f| File.file?(f) && exts.include?(File.extname(f).downcase) }
end

def resize_image(img, width, height, mode, percent)
  if percent > 0
    return img.scale(percent / 100.0)
  end
  case mode
  when 'fit'
    img.resize_to_fit(width, height)
  when 'fill'
    img.resize_to_fill(width, height)
  when 'stretch'
    img.resize(width, height)
  else
    img.resize_to_fit(width, height)
  end
end

files = get_files(input, options[:recursive])
if files.empty?
  puts "Нет изображений."
  exit 1
end

total = files.size
processed = 0
out_format = options[:format] || 'jpg'
files.each do |f|
  out_file = output
  if File.directory?(output)
    rel = Pathname.new(f).relative_path_from(Pathname.new(input)).to_s
    out_dir = File.join(output, File.dirname(rel))
    FileUtils.mkdir_p(out_dir)
    base = File.basename(f, '.*')
    ext = options[:format] || File.extname(f)
    out_file = File.join(out_dir, base + ext)
  end
  begin
    img = Image.read(f).first
    img = resize_image(img, options[:width], options[:height], options[:mode], options[:percent])
    # Конвертация для JPG
    if out_format =~ /jpe?g/
      img = img.quantize(256, GRAYColorspace) unless img.color_space == SRGBColorspace
      img.alpha(AlphaChannel::Deactivate) if img.alpha?
    end
    img.write(out_file) { self.quality = options[:quality] if out_format =~ /jpe?g|webp/ }
    processed += 1
    if options[:verbose]
      puts "✅ #{f} -> #{out_file}"
    else
      pct = processed * 100 / total
      print "\r[#{'█' * (pct/2)}#{'░' * (50 - pct/2)}] #{pct}% #{processed}/#{total}"
    end
  rescue => e
    puts "\n❌ Ошибка #{f}: #{e.message}"
  end
end
puts "\n✅ Обработано #{processed} файлов." unless options[:verbose]
