// resizer.cpp
#include <opencv2/opencv.hpp>
#include <iostream>
#include <vector>
#include <string>
#include <filesystem>
#include <algorithm>
#include <cmath>

using namespace std;
using namespace cv;
namespace fs = std::filesystem;

vector<string> getImageFiles(const string& path, bool recursive) {
    vector<string> files;
    vector<string> exts = {".png", ".jpg", ".jpeg", ".bmp", ".tiff", ".webp"};
    if (fs::is_regular_file(path)) {
        string ext = fs::path(path).extension().string();
        transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
        if (find(exts.begin(), exts.end(), ext) != exts.end())
            files.push_back(path);
        return files;
    }
    if (recursive) {
        for (const auto& entry : fs::recursive_directory_iterator(path)) {
            if (entry.is_regular_file()) {
                string ext = entry.path().extension().string();
                transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
                if (find(exts.begin(), exts.end(), ext) != exts.end())
                    files.push_back(entry.path().string());
            }
        }
    } else {
        for (const auto& entry : fs::directory_iterator(path)) {
            if (entry.is_regular_file()) {
                string ext = entry.path().extension().string();
                transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
                if (find(exts.begin(), exts.end(), ext) != exts.end())
                    files.push_back(entry.path().string());
            }
        }
    }
    return files;
}

Mat resizeImage(const Mat& img, int width, int height, const string& mode, int percent) {
    Mat result;
    if (percent > 0) {
        double scale = percent / 100.0;
        resize(img, result, Size(), scale, scale, INTER_LANCZOS4);
        return result;
    }
    Size target(width, height);
    if (mode == "fit") {
        double ratio = min((double)width / img.cols, (double)height / img.rows);
        Size newSize((int)(img.cols * ratio), (int)(img.rows * ratio));
        resize(img, result, newSize, 0, 0, INTER_LANCZOS4);
    } else if (mode == "fill") {
        double ratio = max((double)width / img.cols, (double)height / img.rows);
        Size newSize((int)(img.cols * ratio), (int)(img.rows * ratio));
        Mat scaled;
        resize(img, scaled, newSize, 0, 0, INTER_LANCZOS4);
        Rect roi((newSize.width - width)/2, (newSize.height - height)/2, width, height);
        result = scaled(roi);
    } else if (mode == "stretch") {
        resize(img, result, target, 0, 0, INTER_LANCZOS4);
    }
    return result;
}

bool processImage(const string& input, const string& output, int width, int height,
                  const string& mode, int quality, int percent, const string& format) {
    Mat img = imread(input, IMREAD_UNCHANGED);
    if (img.empty()) return false;
    Mat resized = resizeImage(img, width, height, mode, percent);
    vector<int> params;
    if (format == "jpg" || format == "jpeg") {
        params.push_back(IMWRITE_JPEG_QUALITY);
        params.push_back(quality);
        if (img.channels() == 4) {
            Mat rgb;
            cvtColor(resized, rgb, COLOR_BGRA2BGR);
            resized = rgb;
        }
    }
    return imwrite(output, resized, params);
}

int main(int argc, char* argv[]) {
    string input, output;
    int width = 0, height = 0, percent = 0, quality = 90;
    string mode = "fit";
    bool recursive = false, verbose = false;
    string format = "";

    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];
        if (arg == "-W" && i+1 < argc) width = stoi(argv[++i]);
        else if (arg == "-H" && i+1 < argc) height = stoi(argv[++i]);
        else if (arg == "-p" && i+1 < argc) percent = stoi(argv[++i]);
        else if (arg == "-m" && i+1 < argc) mode = argv[++i];
        else if (arg == "-q" && i+1 < argc) quality = stoi(argv[++i]);
        else if (arg == "-r") recursive = true;
        else if (arg == "-f" && i+1 < argc) format = argv[++i];
        else if (arg == "-v") verbose = true;
        else if (arg == "-h" || arg == "--help") {
            cout << "Usage: resizer <input> [output] [options]\n"
                 << "  -W <N>     Width\n"
                 << "  -H <N>     Height\n"
                 << "  -p <N>     Percent\n"
                 << "  -m <mode>  fit|fill|stretch\n"
                 << "  -q <N>     Quality\n"
                 << "  -r         Recursive\n"
                 << "  -f <ext>   Output format\n"
                 << "  -v         Verbose\n";
            return 0;
        } else if (input.empty()) input = arg;
        else if (output.empty()) output = arg;
    }
    if (input.empty()) { cerr << "Укажите входной файл или папку." << endl; return 1; }
    if (width == 0 && height == 0 && percent == 0) {
        cerr << "Укажите ширину/высоту или процент." << endl;
        return 1;
    }
    if (output.empty()) {
        if (fs::is_directory(input)) output = input;
        else {
            string base = fs::path(input).stem().string();
            string ext = format.empty() ? fs::path(input).extension().string().substr(1) : format;
            output = base + "_resized." + ext;
        }
    }

    auto files = getImageFiles(input, recursive);
    if (files.empty()) { cerr << "Нет изображений." << endl; return 1; }
    int total = files.size(), processed = 0;
    string outFormat = format.empty() ? "jpg" : format;
    for (const string& f : files) {
        string outFile = output;
        if (fs::is_directory(output)) {
            string rel = fs::relative(f, input).string();
            string outDir = fs::path(output) / fs::path(rel).parent_path();
            fs::create_directories(outDir);
            string base = fs::path(rel).stem().string();
            string ext = format.empty() ? fs::path(f).extension().string().substr(1) : format;
            outFile = (outDir / (base + "." + ext)).string();
        } else {
            // если output - файл, просто используем его
        }
        if (processImage(f, outFile, width, height, mode, quality, percent, outFormat)) {
            processed++;
            if (verbose) cout << "✅ " << f << " -> " << outFile << endl;
        } else {
            cerr << "❌ Ошибка " << f << endl;
        }
        if (!verbose) {
            int pct = (int)(processed * 100.0 / total);
            cout << "\r[" << string(pct/2, '#') << string(50-pct/2, ' ') << "] " << pct << "% " << processed << "/" << total << flush;
        }
    }
    if (!verbose) cout << endl;
    cout << "✅ Обработано " << processed << " файлов." << endl;
    return 0;
}
