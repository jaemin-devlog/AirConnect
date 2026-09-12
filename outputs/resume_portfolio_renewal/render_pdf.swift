import AppKit
import Foundation
import PDFKit

guard CommandLine.arguments.count >= 3 else {
    fputs("usage: render_pdf.swift <pdf> <out-dir> [scale]\n", stderr)
    exit(2)
}

let pdfURL = URL(fileURLWithPath: CommandLine.arguments[1])
let outDir = URL(fileURLWithPath: CommandLine.arguments[2])
let scale = CommandLine.arguments.count >= 4 ? (Double(CommandLine.arguments[3]) ?? 2.0) : 2.0

guard let document = PDFDocument(url: pdfURL) else {
    fputs("failed to open PDF\n", stderr)
    exit(1)
}

try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

for index in 0..<document.pageCount {
    guard let page = document.page(at: index) else { continue }
    let bounds = page.bounds(for: .mediaBox)
    let targetSize = NSSize(width: bounds.width * scale, height: bounds.height * scale)
    let image = page.thumbnail(of: targetSize, for: .mediaBox)

    guard let tiff = image.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: tiff),
          let data = bitmap.representation(using: .png, properties: [:]) else {
        fputs("failed to render page \(index + 1)\n", stderr)
        continue
    }

    let outURL = outDir.appendingPathComponent(String(format: "page-%02d.png", index + 1))
    try data.write(to: outURL)
}

print("rendered \(document.pageCount) pages to \(outDir.path)")
