import xml.etree.ElementTree as ET


REPORT = "/Users/rhee/AirConnect2/build/reports/jacoco/test/jacocoTestReport.xml"


def pct(covered: int, total: int) -> float:
    return round(covered / total * 100, 1) if total else 0.0


def main() -> None:
    root = ET.parse(REPORT).getroot()
    print("overall")
    for counter in root.findall("counter"):
        covered = int(counter.get("covered", 0))
        total = covered + int(counter.get("missed", 0))
        print(counter.get("type"), covered, total, pct(covered, total))

    print("\npackages-line>=40")
    for package in root.findall("package"):
        counters = {
            counter.get("type"): (
                int(counter.get("covered", 0)),
                int(counter.get("covered", 0)) + int(counter.get("missed", 0)),
            )
            for counter in package.findall("counter")
        }
        covered, total = counters.get("LINE", (0, 0))
        if total and covered / total >= 0.4:
            print(package.get("name"), covered, total, pct(covered, total))


if __name__ == "__main__":
    main()
