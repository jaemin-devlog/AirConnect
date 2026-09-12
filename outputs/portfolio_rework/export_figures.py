from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

import build_portfolio12_rework as bp


OUT_DIR = Path(__file__).resolve().parent / "figures"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def save(img: Image.Image, name: str) -> Path:
    path = OUT_DIR / name
    img.save(path)
    return path


def crop(img: Image.Image, box) -> Image.Image:
    return img.crop(box)


def make_architecture():
    img = Image.new("RGB", (1480, 860), bp.BG)
    draw = ImageDraw.Draw(img)
    bp.rounded(draw, (0, 0, 1479, 859), radius=28, fill=bp.BG, outline="#E9DAB4", width=2)

    bp.rounded(draw, (40, 118, 470, 354), radius=28, fill=bp.PANEL_ALT, outline=bp.BORDER, width=2)
    bp.draw_text(draw, (255, 188), "App Client", 44, bold=True, anchor="mm")
    bp.draw_text(draw, (255, 248), "iOS / Android", 26, bold=True, fill=bp.MUTED, anchor="mm")

    bp.rounded(draw, (530, 92, 960, 380), radius=32, fill=bp.WHITE, outline="#8FB0E5", width=3)
    bp.draw_text(draw, (745, 158), "Spring Boot API Server", 48, bold=True, fill=bp.BLUE, anchor="mm")
    bp.paragraph(draw, (582, 220), "클라이언트 요청 수신\n스토어 / 광고 검증 요청\n주문 / 티켓 / 상태 변경 처리", 320, 24, fill=bp.MUTED, line_gap=8)

    bp.rounded(draw, (1020, 70, 1420, 300), radius=28, fill="#EEF4FF", outline="#8FB0E5", width=2)
    bp.draw_text(draw, (1220, 120), "외부 검증", 32, bold=True, fill=bp.BLUE, anchor="mm")
    bp.paragraph(draw, (1060, 168), "App Store\nGoogle Play\nAdMob SSV", 240, 28, bold=True, fill=bp.TEXT, line_gap=8)

    bp.rounded(draw, (1020, 372, 1420, 604), radius=28, fill="#F5FBF7", outline="#A6D5BC", width=2)
    bp.draw_text(draw, (1220, 420), "운영 확인", 32, bold=True, fill=bp.GREEN, anchor="mm")
    bp.paragraph(draw, (1060, 468), "Admin Console\n정합성 점검 / Audit Log / API 통계", 280, 28, bold=True, line_gap=8)

    bp.rounded(draw, (460, 520, 740, 760), radius=26, fill=bp.WHITE, outline=bp.BORDER, width=2)
    bp.draw_text(draw, (600, 590), "MySQL", 36, bold=True, anchor="mm")
    bp.paragraph(draw, (500, 646), "Order / Ticket / Audit Log", 220, 24, bold=True, fill=bp.MUTED, line_gap=8)

    bp.rounded(draw, (760, 520, 1020, 760), radius=26, fill=bp.WHITE, outline=bp.BORDER, width=2)
    bp.draw_text(draw, (890, 590), "Redis", 36, bold=True, anchor="mm")
    bp.paragraph(draw, (792, 646), "Reward Session /\nRefresh Token", 200, 24, bold=True, fill=bp.MUTED, line_gap=8)

    bp.arrow(draw, (470, 238), (530, 238), fill=bp.BLUE, width=5)
    bp.arrow(draw, (960, 216), (1020, 184), fill=bp.BLUE, width=5)
    bp.arrow(draw, (960, 274), (1020, 486), fill=bp.GREEN, width=5)
    bp.arrow(draw, (745, 380), (600, 520), fill=bp.BLUE, width=5)
    bp.arrow(draw, (745, 380), (890, 520), fill=bp.BLUE, width=5)

    bp.rounded(draw, (36, 790, 1444, 840), radius=20, fill="#FFF5D8", outline=bp.BORDER, width=1)
    bp.draw_text(draw, (60, 804), "핵심: 서버 스펙보다 검증 흐름을 강조하고, 결제 / 광고 / 티켓 / 운영을 같은 상태 변경 구조 안에서 설명하도록 재구성했습니다.", 22, bold=True, fill="#8A6300")
    return img


def make_iap_sequence():
    slide = bp.slide_05()
    return crop(slide, (62, 276, 930, 570))


def make_iap_exceptions():
    slide = bp.slide_05()
    return crop(slide, (960, 278, 1538, 568))


def make_iap_race():
    slide = bp.slide_05()
    return crop(slide, (62, 606, 742, 788))


def make_admob_state():
    slide = bp.slide_06()
    return crop(slide, (62, 286, 922, 790))


def make_ticket_consistency_flow():
    slide = bp.slide_07()
    return crop(slide, (62, 286, 702, 792))


def make_admin_consistency():
    return bp.make_consistency_mock()


def make_admin_audit_metrics():
    return bp.make_dashboard_mock()


def add_title_band(img: Image.Image, title: str, subtitle: str | None = None) -> Image.Image:
    canvas = Image.new("RGB", (img.width, img.height + 92), bp.BG)
    canvas.paste(img, (0, 92))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((0, 0, canvas.width - 1, 91), radius=0, fill=bp.PANEL_ALT, outline=bp.BORDER, width=1)
    bp.draw_text(draw, (28, 20), title, 28, bold=True)
    if subtitle:
        bp.draw_text(draw, (28, 56), subtitle, 16, bold=True, fill=bp.MUTED)
    return canvas


def build():
    outputs = {
        "01_airconnect_architecture.png": add_title_band(
            make_architecture(),
            "AirConnect 전체 구조도",
            "결제 / 광고 보상 / 티켓 / 관리자 기능 연결",
        ),
        "02_iap_verification_sequence.png": add_title_band(
            make_iap_sequence(),
            "IAP 결제 검증 시퀀스",
            "클라이언트 영수증을 서버가 직접 검증하는 흐름",
        ),
        "03_iap_exception_cases_table.png": add_title_band(
            make_iap_exceptions(),
            "IAP 예외 케이스 테스트 표",
            "직접 확인한 예외 기준 중심",
        ),
        "04_iap_race_timeline.png": add_title_band(
            make_iap_race(),
            "동시 요청 레이스 타임라인",
            "1건 지급 / 1건 이미 처리됨으로 수렴",
        ),
        "05_admob_ssv_state_transition.png": add_title_band(
            make_admob_state(),
            "AdMob SSV 상태 전이도",
            "반복 콜백에도 재화 상태는 한 번만 변경",
        ),
        "06_ticket_consistency_flow.png": add_title_band(
            make_ticket_consistency_flow(),
            "티켓 원장 / 잔액 정합성 점검 흐름",
            "Admin Console PASS / FAIL 점검",
        ),
        "07_admin_consistency_screen.png": add_title_band(
            make_admin_consistency(),
            "Admin Console - 정합성 점검 화면",
            "7개 항목 PASS / FAIL 확인용",
        ),
        "08_admin_audit_metrics_screen.png": add_title_band(
            make_admin_audit_metrics(),
            "Admin Console - Audit Log / API 통계",
            "운영 로그와 사용량 지표 확인용",
        ),
    }

    paths = []
    for name, image in outputs.items():
        paths.append(save(image, name))
    return paths


if __name__ == "__main__":
    for path in build():
        print(path)
