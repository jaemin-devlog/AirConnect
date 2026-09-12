from pathlib import Path

from pypdf import PdfReader


FILES = [
    (
        "template",
        "/Users/rhee/Library/CloudStorage/OneDrive-개인/취업준비/생각등대/생각등대_이력서_템플릿.pdf",
    ),
    ("old_resume", "/Users/rhee/Downloads/이강현 이력서.pdf"),
]


def main() -> None:
    out_dir = Path("/Users/rhee/AirConnect2/outputs/resume_rewrite")
    out_dir.mkdir(parents=True, exist_ok=True)

    for name, file_path in FILES:
        reader = PdfReader(file_path)
        parts = []
        for index, page in enumerate(reader.pages, 1):
            text = (page.extract_text() or "").replace(chr(0), "").strip()
            parts.append(f"--- PAGE {index} ---\n{text}\n")

        output = out_dir / f"{name}_text.txt"
        output.write_text("\n".join(parts), encoding="utf-8")
        print(name, len(reader.pages), sum(len(part) for part in parts), output)


if __name__ == "__main__":
    main()
