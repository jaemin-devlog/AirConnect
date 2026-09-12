from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont, ImageOps, JpegImagePlugin  # noqa: F401


ROOT = Path(__file__).resolve().parent
SLIDES_DIR = ROOT / "slides"
PDF_PATH = ROOT / "이강현_Portfolio12_개정본.pdf"

W = 1600
H = 900
MARGIN_X = 64
MARGIN_Y = 42

BG = "#F8F1DE"
PANEL = "#FFF9ED"
PANEL_ALT = "#FFF3D3"
BORDER = "#E7C97F"
TEXT = "#2F2920"
MUTED = "#7A664B"
ACCENT = "#F3B500"
ACCENT_SOFT = "#FBE6A8"
BLUE = "#325D9D"
GREEN = "#2D9D67"
RED = "#D55A54"
GRAY = "#EAE0C9"
LINE = "#CDBFA1"
WHITE = "#FFFFFF"

FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
ICON_PATH = Path(
    "/Users/rhee/AirConnect2/outputs/manual-20260605-airconnect-admin/presentations/airconnect-admin-section/assets/app_icon.png"
)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    index = 1 if bold else 0
    return ImageFont.truetype(FONT_PATH, size=size, index=index)


def ensure_dirs() -> None:
    SLIDES_DIR.mkdir(parents=True, exist_ok=True)


def rounded(draw: ImageDraw.ImageDraw, box, radius=28, fill=PANEL, outline=BORDER, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_text(
    draw: ImageDraw.ImageDraw,
    xy,
    text: str,
    size: int,
    *,
    bold: bool = False,
    fill: str = TEXT,
    anchor: str | None = None,
):
    draw.text(xy, text, font=font(size, bold), fill=fill, anchor=anchor)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, max_width: int, size: int, *, bold: bool = False) -> list[str]:
    f = font(size, bold)
    lines: list[str] = []
    for para in text.split("\n"):
        words = para.split(" ")
        if not words:
            lines.append("")
            continue
        current = words[0]
        for word in words[1:]:
            trial = f"{current} {word}"
            width = draw.textbbox((0, 0), trial, font=f)[2]
            if width <= max_width:
                current = trial
            else:
                lines.append(current)
                current = word
        lines.append(current)
    return lines


def paragraph(
    draw: ImageDraw.ImageDraw,
    xy,
    text: str,
    max_width: int,
    size: int,
    *,
    bold: bool = False,
    fill: str = TEXT,
    line_gap: int = 10,
) -> int:
    x, y = xy
    lines = wrap_text(draw, text, max_width, size, bold=bold)
    f = font(size, bold)
    ascent, descent = f.getmetrics()
    line_height = ascent + descent + line_gap
    for line in lines:
        draw.text((x, y), line, font=f, fill=fill)
        y += line_height
    return y


def pill(draw: ImageDraw.ImageDraw, xy, text: str, *, fill=ACCENT_SOFT, text_fill="#8A6300"):
    x, y = xy
    w = draw.textbbox((0, 0), text, font=font(20, True))[2] + 34
    h = 38
    rounded(draw, (x, y, x + w, y + h), radius=19, fill=fill, outline=BORDER, width=1)
    draw_text(draw, (x + 18, y + h / 2), text, 20, bold=True, fill=text_fill, anchor="lm")
    return w, h


def page_scaffold(slide_no: int, title_right: str):
    image = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(image)
    rounded(draw, (24, 24, W - 24, H - 24), radius=34, fill=BG, outline="#E9DAB4", width=2)

    if ICON_PATH.exists():
        icon = Image.open(ICON_PATH).convert("RGBA")
        icon = ImageOps.contain(icon, (40, 40))
        image.paste(icon, (58, 42), icon)

    draw_text(draw, (104, 56), "AirConnect", 22, bold=True)
    draw_text(draw, (W - 58, 58), title_right, 16, bold=True, fill=MUTED, anchor="ra")
    draw.line((58, H - 52, W - 58, H - 52), fill="#E2D4B0", width=1)
    draw_text(draw, (W - 58, H - 36), f"{slide_no:02d} / 12", 16, bold=True, fill=MUTED, anchor="ra")
    return image, draw


def footer_note(draw: ImageDraw.ImageDraw, text: str):
    draw_text(draw, (58, H - 36), text, 14, bold=True, fill=MUTED)


def panel_title(draw: ImageDraw.ImageDraw, x: int, y: int, title: str, sub: str | None = None):
    draw_text(draw, (x, y), title, 26, bold=True)
    if sub:
        paragraph(draw, (x, y + 36), sub, 420, 16, fill=MUTED, line_gap=6)


def arrow(draw: ImageDraw.ImageDraw, start, end, *, fill=BORDER, width=4):
    draw.line([start, end], fill=fill, width=width)
    x1, y1 = end
    if abs(end[0] - start[0]) >= abs(end[1] - start[1]):
        sign = 1 if end[0] > start[0] else -1
        draw.polygon([(x1, y1), (x1 - 16 * sign, y1 - 8), (x1 - 16 * sign, y1 + 8)], fill=fill)
    else:
        sign = 1 if end[1] > start[1] else -1
        draw.polygon([(x1, y1), (x1 - 8, y1 - 16 * sign), (x1 + 8, y1 - 16 * sign)], fill=fill)


def info_box(draw: ImageDraw.ImageDraw, box, title: str, body: str, *, accent=ACCENT):
    rounded(draw, box, radius=24, fill=PANEL, outline=BORDER, width=2)
    x1, y1, x2, y2 = box
    draw.rounded_rectangle((x1 + 16, y1 + 16, x1 + 120, y1 + 48), radius=16, fill=ACCENT_SOFT, outline=None)
    draw_text(draw, (x1 + 28, y1 + 24), title, 16, bold=True, fill="#8A6300")
    paragraph(draw, (x1 + 22, y1 + 64), body, x2 - x1 - 44, 18, line_gap=8)


def simple_table(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    widths: list[int],
    headers: list[str],
    rows: list[list[str]],
    *,
    row_height: int = 48,
    font_size: int = 16,
):
    total_w = sum(widths)
    rounded(draw, (x, y, x + total_w, y + row_height * (len(rows) + 1)), radius=18, fill=WHITE, outline=BORDER, width=2)
    cx = x
    for idx, width in enumerate(widths):
        draw.rounded_rectangle((cx, y, cx + width, y + row_height), radius=0, fill=ACCENT_SOFT)
        draw_text(draw, (cx + 14, y + row_height / 2), headers[idx], font_size, bold=True, anchor="lm")
        if idx > 0:
            draw.line((cx, y, cx, y + row_height * (len(rows) + 1)), fill=BORDER, width=1)
        cx += width
    for ridx, row in enumerate(rows):
        top = y + row_height * (ridx + 1)
        draw.line((x, top, x + total_w, top), fill="#EADDBB", width=1)
        cx = x
        for cidx, width in enumerate(widths):
            body = row[cidx]
            lines = wrap_text(draw, body, width - 20, font_size, bold=False)
            yy = top + 10
            for line in lines[:2]:
                draw_text(draw, (cx + 10, yy), line, font_size, fill=TEXT)
                yy += font_size + 4
            cx += width


def draw_screen_card(draw: ImageDraw.ImageDraw, image: Image.Image, box, title: str):
    x1, y1, x2, y2 = box
    rounded(draw, box, radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (x1 + 24, y1 + 22), title, 22, bold=True)
    inner = (x1 + 22, y1 + 60, x2 - 22, y2 - 22)
    rounded(draw, inner, radius=22, fill=WHITE, outline="#ECDDB6", width=1)
    screen = ImageOps.contain(image, (inner[2] - inner[0] - 10, inner[3] - inner[1] - 10))
    px = inner[0] + ((inner[2] - inner[0]) - screen.width) // 2
    py = inner[1] + ((inner[3] - inner[1]) - screen.height) // 2
    image.paste(screen, (px, py))


def make_dashboard_mock() -> Image.Image:
    image = Image.new("RGB", (1200, 680), "#FFFDF7")
    draw = ImageDraw.Draw(image)
    rounded(draw, (0, 0, 1199, 679), radius=26, fill="#FFFDF7", outline=BORDER, width=2)
    rounded(draw, (24, 24, 1176, 92), radius=20, fill="#FFF5DC", outline=BORDER, width=1)
    draw_text(draw, (42, 46), "운영 지표 대시보드", 30, bold=True)
    draw_text(draw, (1130, 50), "최근 7일 기준", 18, bold=True, fill=MUTED, anchor="ra")
    stats = [
        ("누적 Audit Log", "1,047+"),
        ("API 호출", "117건"),
        ("고유 엔드포인트", "24개"),
        ("정합성 점검", "7개 PASS"),
    ]
    sx = 36
    for label, value in stats:
        rounded(draw, (sx, 122, sx + 262, 242), radius=24, fill=PANEL_ALT, outline=BORDER, width=1)
        draw_text(draw, (sx + 18, 146), label, 20, bold=True, fill=MUTED)
        draw_text(draw, (sx + 18, 184), value, 34, bold=True)
        sx += 278
    rounded(draw, (36, 272, 700, 640), radius=24, fill=WHITE, outline=BORDER, width=1)
    draw_text(draw, (58, 298), "API 사용량", 24, bold=True)
    bars = [74, 58, 46, 39, 31]
    labels = ["GET /matches", "GET /tickets", "POST /iap", "GET /admin", "POST /ads"]
    for idx, (bar, label) in enumerate(zip(bars, labels)):
        y = 350 + idx * 52
        draw_text(draw, (58, y), label, 18, fill=MUTED)
        draw.rounded_rectangle((240, y - 2, 240 + bar * 5, y + 22), radius=10, fill="#6F95D5")
        draw_text(draw, (240 + bar * 5 + 14, y + 10), f"{bar}", 18, bold=True, anchor="lm")
    rounded(draw, (728, 272, 1164, 640), radius=24, fill=WHITE, outline=BORDER, width=1)
    draw_text(draw, (752, 298), "감사 로그", 24, bold=True)
    headers = ["시간", "액션", "대상", "결과"]
    cols = [102, 118, 110, 72]
    x = 752
    for i, h in enumerate(headers):
        draw_text(draw, (x, 336), h, 18, bold=True, fill=MUTED)
        x += cols[i]
    entries = [
        ("10:24", "정합성 점검", "ticket", "PASS"),
        ("10:27", "신고 검토", "user 182", "DONE"),
        ("11:02", "티켓 조정", "user 74", "DONE"),
        ("11:36", "API 통계 조회", "dashboard", "OK"),
        ("12:14", "공지 발송", "all users", "DONE"),
    ]
    for ridx, row in enumerate(entries):
        top = 364 + ridx * 48
        draw.line((748, top - 12, 1146, top - 12), fill="#EEDFB7", width=1)
        x = 752
        for cidx, value in enumerate(row):
            color = GREEN if value == "PASS" else TEXT
            draw_text(draw, (x, top), value, 18, bold=(value in {"PASS", "DONE", "OK"}), fill=color)
            x += cols[cidx]
    return image


def make_consistency_mock() -> Image.Image:
    image = Image.new("RGB", (1200, 680), "#FFFDF7")
    draw = ImageDraw.Draw(image)
    rounded(draw, (0, 0, 1199, 679), radius=26, fill="#FFFDF7", outline=BORDER, width=2)
    rounded(draw, (24, 24, 1176, 92), radius=20, fill="#FFF5DC", outline=BORDER, width=1)
    draw_text(draw, (42, 46), "데이터 정합성 점검", 30, bold=True)
    draw_text(draw, (1130, 50), "전체 7개 항목", 18, bold=True, fill=MUTED, anchor="ra")
    draw_text(draw, (42, 118), "점검 결과", 22, bold=True)
    rows = [
        ("잔액과 이력 합계 일치", "balance = sum(history)", "PASS"),
        ("지급 경로별 중복 이력 없음", "refType + refId unique", "PASS"),
        ("IAP 검증 완료 후 미지급 주문 없음", "verified order -> granted", "PASS"),
        ("광고 보상 세션 만료 처리 확인", "TTL 이후 EXPIRED", "PASS"),
        ("처리 완료 세션 중복 지급 없음", "REWARDED 1회", "PASS"),
        ("환불 반영 후 장부 기록 존재", "refund ledger exists", "PASS"),
        ("운영자 수동 지급 근거 기록", "admin action logged", "PASS"),
    ]
    headers = ["점검 항목", "정상 기준", "결과"]
    widths = [540, 360, 180]
    simple_table(draw, 42, 152, widths, headers, [list(r) for r in rows], row_height=58, font_size=18)
    rounded(draw, (42, 628, 1160, 654), radius=14, fill="#E8F7EF", outline="#B4DEC5", width=1)
    draw_text(draw, (62, 633), "불일치 강제 주입 테스트에서도 FAIL 감지가 가능한 구조로 점검 로직을 검증했습니다.", 17, bold=True, fill=GREEN)
    for ridx, row in enumerate(rows):
        top = 152 + 58 * (ridx + 1)
        draw_text(draw, (1088, top + 20), row[2], 18, bold=True, fill=GREEN, anchor="mm")
    return image


def add_badges(draw: ImageDraw.ImageDraw, x: int, y: int, items: Iterable[str]):
    cx = x
    for item in items:
        w, _ = pill(draw, (cx, y), item, fill="#FFF2C8")
        cx += w + 10


def slide_01():
    image, draw = page_scaffold(1, "BACKEND DEVELOPER")
    draw_text(draw, (64, 150), "이강현", 70, bold=True)
    draw_text(draw, (64, 225), "Lee Kanghyun", 28, bold=True, fill=MUTED)
    paragraph(
        draw,
        (64, 286),
        "팀에 필요한 일을 빠르게 배우고 검증 가능한 결과로 연결하는 백엔드 개발자입니다.",
        660,
        28,
        bold=True,
        line_gap=10,
    )
    add_badges(draw, 64, 376, ["AirConnect 운영", "IAP / AdMob 검증", "운영 지표 / Admin"])

    info_box(draw, (64, 458, 496, 774), "AIRCONNECT", "iOS / Android 출시 후 운영\n가입 19명 / 온보딩 완료 12명\n결제 / 광고 보상 / 티켓 / 관리자 기능 구현")
    info_box(draw, (528, 458, 952, 774), "MONEYWAY", "한국관광공사 2025 공모전 우수상\nAI 일정 생성 검증 파이프라인 구현\np95 47.1% 서버 내부 처리 개선")
    info_box(draw, (984, 458, 1536, 774), "FOCUS", "상태 변경 정확성이 중요한 도메인에서\n중복 지급 방지 / 검증 흐름 / 운영 추적성을\n설계하고 구현한 경험을 포트폴리오로 정리했습니다.")
    footer_note(draw, "포트폴리오 개정본: AirConnect 구조와 검증 흐름 중심 재구성")
    return image


def slide_02():
    image, draw = page_scaffold(2, "WHY FLEX")
    pill(draw, (64, 108), "WHY FLEX")
    paragraph(
        draw,
        (64, 170),
        "상태 변경의 정확성이 중요한 도메인에서 검증 가능한 결과를 만드는 백엔드 개발자입니다.",
        940,
        42,
        bold=True,
        line_gap=12,
    )
    cards = [
        ("01", "서버가 신뢰할 수 있는 상태 변경을 만들도록 검증했습니다.", "AirConnect에서 결제 / 광고 보상 / 티켓 지급을 다루며 클라이언트 요청을 그대로 믿지 않고 서버 검증과 멱등 처리로 중복 지급과 위변조를 막았습니다."),
        ("02", "운영 중 문제를 화면이 아니라 지표와 로그로 확인했습니다.", "Admin Console에서 Audit Log / API 호출 통계 / 정합성 점검 결과를 확인할 수 있도록 구성했습니다. 문제가 생긴 뒤 추측하지 않도록 운영 구조를 설계했습니다."),
        ("03", "팀에 필요한 영역이라면 빠르게 배우고 결과로 연결했습니다.", "MoneyWay에서는 OpenAI API 사용 자체보다 결과 신뢰성이 중요하다고 판단했고, 후보 제한 / JSON 검증 / 예산 검증 / 성능 계측까지 직접 구성해 시연 가능한 결과물로 연결했습니다."),
    ]
    y = 316
    for idx, (no, title, body) in enumerate(cards):
        box = (64 + idx * 500, y, 520 + idx * 500, 760)
        rounded(draw, box, radius=30, fill=PANEL, outline=BORDER, width=2)
        draw.rounded_rectangle((box[0] + 18, box[1] + 18, box[0] + 82, box[1] + 62), radius=18, fill=ACCENT_SOFT)
        draw_text(draw, (box[0] + 36, box[1] + 26), no, 22, bold=True, fill="#8A6300")
        paragraph(draw, (box[0] + 22, box[1] + 92), title, 420, 28, bold=True, line_gap=8)
        paragraph(draw, (box[0] + 22, box[1] + 220), body, 420, 18, fill=MUTED, line_gap=8)
    footer_note(draw, "이 포트폴리오는 상태 검증 / 운영 추적 / 빠른 학습 전환 세 축으로 정리했습니다.")
    return image


def slide_03():
    image, draw = page_scaffold(3, "Projects")
    pill(draw, (64, 108), "Projects")
    draw_text(draw, (64, 170), "2024 - 2026", 22, bold=True, fill=MUTED)
    draw_text(draw, (64, 214), "주요 프로젝트", 46, bold=True)
    rounded(draw, (64, 300, 944, 754), radius=30, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (92, 336), "AirConnect", 40, bold=True)
    draw_text(draw, (92, 384), "대학생 친구찾기 애플리케이션", 22, bold=True, fill=MUTED)
    add_badges(draw, 92, 430, ["IAP", "AdMob SSV", "1:1 Matching", "Admin", "Moderation", "Ticket"])
    paragraph(draw, (92, 504), "백엔드 핵심 도메인 담당\n출시 후 운영 / 가입 19명 / 온보딩 완료 12명\n정식 홍보 전 기능 검증 단계", 400, 26, bold=True, line_gap=10)
    paragraph(draw, (520, 504), "핵심 포인트\n- 결제와 광고 보상 검증\n- 티켓 원장과 잔액 정합성\n- 관리자 운영 지표와 감사 로그", 360, 22, fill=MUTED, line_gap=10)

    rounded(draw, (976, 300, 1536, 754), radius=30, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (1004, 336), "MoneyWay", 40, bold=True)
    draw_text(draw, (1004, 384), "제주 AI 여행 일정 생성 서비스", 22, bold=True, fill=MUTED)
    add_badges(draw, 1004, 430, ["TourAPI", "OpenAI", "Validation", "Perf"])
    paragraph(draw, (1004, 504), "백엔드 담당\n장소 데이터 수집 / 장소 조회 API / AI 일정 생성 검증 파이프라인 / 성능 계측", 460, 24, bold=True, line_gap=10)
    paragraph(draw, (1004, 640), "우수상 / p95 47.1% 서버 내부 처리 개선\n30개 변형 테스트 0건 오류", 460, 22, fill=MUTED, line_gap=10)
    footer_note(draw, "AirConnect를 중심 프로젝트로, MoneyWay를 검증 파이프라인 사례로 배치했습니다.")
    return image


def slide_04():
    image, draw = page_scaffold(4, "AIRCONNECT Architecture")
    pill(draw, (64, 108), "AIRCONNECT Architecture")
    paragraph(
        draw,
        (64, 170),
        "결제 / 광고 보상 / 티켓 / 관리자 기능이 하나의 검증 흐름으로 연결되도록 단순화했습니다.",
        900,
        40,
        bold=True,
        line_gap=12,
    )
    rounded(draw, (92, 310, 528, 454), radius=28, fill=PANEL_ALT, outline=BORDER, width=2)
    draw_text(draw, (310, 350), "App Client", 38, bold=True, anchor="mm")
    draw_text(draw, (310, 396), "iOS / Android", 24, bold=True, fill=MUTED, anchor="mm")

    rounded(draw, (586, 286, 1016, 478), radius=32, fill=WHITE, outline="#8FB0E5", width=3)
    draw_text(draw, (801, 340), "Spring Boot API Server", 42, bold=True, fill=BLUE, anchor="mm")
    paragraph(draw, (640, 388), "클라이언트 요청 수신\n스토어 / 광고 검증 요청\n주문 / 티켓 / 상태 변경 처리", 320, 22, fill=MUTED, line_gap=8)

    rounded(draw, (1080, 252, 1490, 416), radius=28, fill="#EEF4FF", outline="#8FB0E5", width=2)
    draw_text(draw, (1285, 300), "외부 검증", 28, bold=True, fill=BLUE, anchor="mm")
    paragraph(draw, (1120, 336), "App Store\nGoogle Play\nAdMob SSV", 240, 24, bold=True, fill=TEXT, line_gap=8)

    rounded(draw, (1080, 472, 1490, 636), radius=28, fill="#F5FBF7", outline="#A6D5BC", width=2)
    draw_text(draw, (1285, 520), "운영 확인", 28, bold=True, fill=GREEN, anchor="mm")
    paragraph(draw, (1120, 556), "Admin Console\n정합성 점검 / Audit Log / API 통계", 290, 24, bold=True, line_gap=8)

    rounded(draw, (520, 566, 800, 754), radius=26, fill=WHITE, outline=BORDER, width=2)
    draw_text(draw, (660, 610), "MySQL", 32, bold=True, anchor="mm")
    paragraph(draw, (556, 652), "Order / Ticket / Audit Log", 220, 22, bold=True, fill=MUTED, line_gap=8)

    rounded(draw, (820, 566, 1080, 754), radius=26, fill=WHITE, outline=BORDER, width=2)
    draw_text(draw, (950, 610), "Redis", 32, bold=True, anchor="mm")
    paragraph(draw, (846, 652), "Reward Session /\nRefresh Token", 200, 22, bold=True, fill=MUTED, line_gap=8)

    arrow(draw, (528, 382), (586, 382), fill=BLUE, width=5)
    arrow(draw, (1016, 364), (1080, 336), fill=BLUE, width=5)
    arrow(draw, (1016, 400), (1080, 554), fill=GREEN, width=5)
    arrow(draw, (801, 478), (660, 566), fill=BLUE, width=5)
    arrow(draw, (801, 478), (950, 566), fill=BLUE, width=5)

    rounded(draw, (64, 786, 1536, 834), radius=20, fill="#FFF5D8", outline=BORDER, width=1)
    draw_text(draw, (88, 801), "핵심: 서버 스펙보다 검증 흐름을 강조하고, 결제 / 광고 / 티켓 / 운영을 같은 상태 변경 구조 안에서 설명하도록 재구성했습니다.", 20, bold=True, fill="#8A6300")
    footer_note(draw, "원본 4페이지의 요소 수를 줄이고 검증 경로를 중심 축으로 다시 배치했습니다.")
    return image


def slide_05():
    image, draw = page_scaffold(5, "CASE 01")
    pill(draw, (64, 108), "CASE 01")
    paragraph(
        draw,
        (64, 168),
        "클라이언트 영수증을 신뢰하지 않고 서버 검증으로 결제 위변조와 중복 지급을 차단했습니다.",
        1120,
        38,
        bold=True,
        line_gap=12,
    )
    rounded(draw, (64, 278, 928, 566), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (90, 306), "IAP 검증 시퀀스", 26, bold=True)
    cols = [120, 340, 240, 120]
    labels = ["Client", "Server", "App Store / Google Play", "Client"]
    cx = 106
    lane_centers = []
    for idx, w in enumerate(cols):
        lane_centers.append(cx + w // 2)
        draw_text(draw, (cx + w // 2, 344), labels[idx], 18, bold=True, fill=MUTED, anchor="mm")
        draw.line((cx + w // 2, 366, cx + w // 2, 544), fill="#DCCFAE", width=2)
        cx += w
    steps = [
        (0, 1, 392, "결제 완료 요청"),
        (1, 2, 430, "스토어 검증 요청"),
        (2, 1, 468, "거래 상태 응답"),
        (1, 1, 506, "거래 고유값 확인 / 주문 잠금 / 티켓 이력 저장 / 잔액 반영"),
        (1, 3, 544, "지급 완료 또는 이미 처리됨"),
    ]
    for s, e, y, label in steps:
        x1 = lane_centers[s]
        x2 = lane_centers[e]
        arrow(draw, (x1, y), (x2, y), fill=BLUE if s != e else GREEN, width=4)
        draw_text(draw, ((x1 + x2) / 2, y - 18), label, 15, bold=True, fill=TEXT, anchor="mm")
    rounded(draw, (960, 278, 1536, 566), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (986, 306), "IAP 예외 케이스 테스트", 26, bold=True)
    rows = [
        ["가짜 서명 영수증", "JWS 서명 / 인증서 위조", "거절", "거절"],
        ["transactionId 불일치", "요청값과 검증값 불일치", "거절", "거절"],
        ["사용자 연결값 불일치", "다른 사용자 토큰", "거절", "거절"],
        ["환경 불일치", "Sandbox / Production 불일치", "거절", "거절"],
        ["환불 / 취소 거래", "revocation 정보 포함", "거절", "거절"],
        ["중복 영수증", "이미 처리된 거래", "이미 처리됨", "이미 처리됨"],
    ]
    simple_table(draw, 986, 344, [184, 186, 96, 96], ["테스트 케이스", "입력 조건", "기대 결과", "실제 결과"], rows, row_height=31, font_size=11)

    rounded(draw, (64, 606, 742, 786), radius=26, fill=WHITE, outline=BORDER, width=2)
    draw_text(draw, (90, 632), "동시 요청 레이스 타임라인", 24, bold=True)
    draw_text(draw, (90, 676), "Time ->", 18, bold=True, fill=MUTED)
    draw_text(draw, (160, 704), "Request A", 20, bold=True)
    draw_text(draw, (450, 704), "Request B", 20, bold=True)
    draw.line((220, 724, 220, 768), fill=BLUE, width=4)
    draw.line((510, 724, 510, 768), fill=BLUE, width=4)
    draw_text(draw, (90, 734), "요청 시작", 15)
    draw_text(draw, (380, 734), "요청 시작", 15)
    draw_text(draw, (90, 754), "주문 잠금 획득", 15, bold=True, fill=GREEN)
    draw_text(draw, (380, 754), "잠금 대기", 15, bold=True, fill=RED)
    draw_text(draw, (90, 774), "지급 처리 후 commit", 15)
    draw_text(draw, (380, 774), "잠금 획득 -> 이미 지급 상태 확인 -> ALREADY_GRANTED", 14)

    rounded(draw, (774, 606, 1536, 786), radius=26, fill="#FFF5D8", outline=BORDER, width=2)
    draw_text(draw, (802, 632), "면접에서 바로 설명할 수 있는 답", 24, bold=True, fill="#8A6300")
    paragraph(
        draw,
        (802, 678),
        "유니크 제약만으로는 동시 검증 구간에서 이미 지급 직전 상태를 안전하게 직렬화하기 어려워 주문 잠금을 함께 사용했습니다.\n\n같은 appAccountToken / transactionId 동시 2회 검증 테스트에서 1건 GRANTED / 1건 ALREADY_GRANTED 로 수렴하는지 확인했습니다.",
        700,
        22,
        bold=True,
        line_gap=10,
    )
    footer_note(draw, "시퀀스 / 테스트 표 / 레이스 타임라인을 한 페이지 안에서 연결해 면접 설명 흐름을 바로 만들 수 있게 했습니다.")
    return image


def slide_06():
    image, draw = page_scaffold(6, "CASE 02")
    pill(draw, (64, 108), "CASE 02")
    paragraph(
        draw,
        (64, 168),
        "AdMob SSV 콜백이 여러 번 와도 티켓은 한 번만 지급되도록 상태 전이로 설명했습니다.",
        1100,
        38,
        bold=True,
        line_gap=12,
    )
    rounded(draw, (64, 286, 920, 790), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (90, 316), "AdMob SSV 상태 전이도", 28, bold=True)

    nodes = {
        "READY": (290, 430),
        "REWARDED": (290, 574),
        "EXPIRED": (650, 632),
        "ALREADY": (650, 500),
        "GRANTED": (650, 352),
    }
    for name, (cx, cy) in nodes.items():
        fill = WHITE
        outline = BORDER
        if name in {"REWARDED", "GRANTED"}:
            fill, outline = "#EEF8F2", "#99D2B3"
        if name == "EXPIRED":
            fill, outline = "#F4F1EA", "#CFBF9B"
        rounded(draw, (cx - 110, cy - 40, cx + 110, cy + 40), radius=24, fill=fill, outline=outline, width=2)
        draw_text(draw, (cx, cy), name, 28, bold=True, anchor="mm", fill=GREEN if name in {"REWARDED", "GRANTED"} else TEXT)
    arrow(draw, (290, 470), (290, 534), fill=GREEN, width=5)
    draw_text(draw, (318, 502), "서명 검증 성공 + 미지급", 17, bold=True, fill=MUTED)
    arrow(draw, (400, 574), (540, 500), fill=BLUE, width=5)
    draw_text(draw, (470, 520), "이미 REWARDED 상태", 17, bold=True, fill=MUTED, anchor="mm")
    arrow(draw, (400, 574), (540, 352), fill=GREEN, width=5)
    draw_text(draw, (500, 420), "티켓 지급 후 응답", 17, bold=True, fill=MUTED, anchor="mm")
    arrow(draw, (290, 470), (540, 688), fill="#A98D50", width=5)
    draw_text(draw, (472, 608), "TTL 만료", 17, bold=True, fill=MUTED, anchor="mm")

    rounded(draw, (114, 684, 770, 778), radius=24, fill="#FFF5D8", outline=BORDER, width=1)
    paragraph(
        draw,
        (140, 706),
        "중복 요청을 막는 것이 아니라\n반복 요청에도 재화 상태가 한 번만 바뀌도록 설계",
        600,
        24,
        bold=True,
        fill="#8A6300",
        line_gap=10,
    )

    rounded(draw, (958, 286, 1536, 790), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (986, 316), "설명 포인트", 28, bold=True)
    bullets = [
        "READY 상태에서만 보상 지급 흐름으로 진입",
        "서명 / key_id / custom_data(sessionKey) / 거래값을 검증한 뒤 처리",
        "같은 콜백이 재시도되어도 REWARDED 상태면 ALREADY_GRANTED 응답",
        "TTL 이후에는 EXPIRED 로 종료해 오래된 세션이 보상으로 이어지지 않도록 차단",
        "핵심은 요청 횟수가 아니라 최종 상태가 한 번만 바뀌는 구조",
    ]
    yy = 376
    for item in bullets:
        rounded(draw, (986, yy, 1508, yy + 74), radius=22, fill=WHITE, outline="#E8D9B2", width=1)
        paragraph(draw, (1012, yy + 18), f"• {item}", 472, 20, bold=True, line_gap=6)
        yy += 88
    footer_note(draw, "기존 설명 문장을 상태 전이 중심으로 바꿔 콜백 재시도와 멱등 처리가 한눈에 보이도록 만들었습니다.")
    return image


def slide_07():
    image, draw = page_scaffold(7, "CASE 03")
    pill(draw, (64, 108), "CASE 03")
    paragraph(
        draw,
        (64, 168),
        "티켓 원장과 잔액 정합성을 Admin Console에서 PASS / FAIL로 점검하는 흐름으로 재구성했습니다.",
        1160,
        38,
        bold=True,
        line_gap=12,
    )
    rounded(draw, (64, 286, 700, 790), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (92, 316), "정합성 점검 흐름", 28, bold=True)
    items = [
        ("IAP 지급", 180, 400, "#EAF2FF"),
        ("AdMob 지급", 180, 500, "#EAFBF0"),
        ("관리자 지급", 180, 600, "#FFF2D8"),
        ("Ticket Transaction 저장", 470, 500, WHITE),
        ("User Ticket Balance 반영", 470, 620, WHITE),
        ("Consistency Checker", 470, 740, "#F3F8FF"),
    ]
    for text, cx, cy, fill in items:
        rounded(draw, (cx - 120, cy - 34, cx + 120, cy + 34), radius=22, fill=fill, outline=BORDER, width=2)
        draw_text(draw, (cx, cy), text, 22, bold=True, anchor="mm")
    arrow(draw, (300, 400), (350, 500), fill=BLUE, width=4)
    arrow(draw, (300, 500), (350, 500), fill=BLUE, width=4)
    arrow(draw, (300, 600), (350, 500), fill=BLUE, width=4)
    arrow(draw, (470, 534), (470, 586), fill=GREEN, width=4)
    arrow(draw, (470, 654), (470, 706), fill=GREEN, width=4)
    rounded(draw, (588, 706, 666, 774), radius=18, fill="#EEF8F2", outline="#B2D8C2", width=1)
    draw_text(draw, (627, 724), "Admin\nConsole", 18, bold=True, anchor="mm", fill=GREEN)
    arrow(draw, (590, 740), (550, 740), fill=GREEN, width=4)
    draw_text(draw, (598, 688), "PASS / FAIL", 18, bold=True, fill=MUTED)

    rounded(draw, (734, 286, 1536, 790), radius=28, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (762, 316), "점검 항목 표", 28, bold=True)
    rows = [
        ["잔액과 이력 합계 일치", "balance = sum(history)", "불일치 시 FAIL"],
        ["지급 경로별 중복 없음", "refType + refId unique", "중복 시 FAIL"],
        ["IAP 검증 완료 후 미지급 없음", "verified order는 지급 완료", "미지급 시 FAIL"],
        ["광고 보상 세션 만료 처리", "TTL 이후 EXPIRED", "미처리 시 FAIL"],
        ["처리 완료 세션 중복 지급 없음", "REWARDED 1회", "중복 시 FAIL"],
    ]
    simple_table(draw, 762, 360, [286, 282, 182], ["점검 항목", "정상 기준", "오류 감지 방식"], rows, row_height=62, font_size=16)
    rounded(draw, (762, 734, 1510, 786), radius=18, fill="#FFF5D8", outline=BORDER, width=1)
    paragraph(
        draw,
        (784, 747),
        "운영자는 로그를 뒤지는 대신 화면에서 바로 PASS / FAIL을 보고, 이슈가 생기면 어떤 기준이 깨졌는지 즉시 확인할 수 있습니다.",
        700,
        17,
        bold=True,
        fill="#8A6300",
        line_gap=4,
    )
    footer_note(draw, "문장으로 설명되던 장부 / 잔액 / 세션 검증을 운영 화면 흐름과 점검 표로 분리했습니다.")
    return image


def slide_08():
    image, draw = page_scaffold(8, "AIRCONNECT - ADMIN CONSOLE")
    pill(draw, (64, 108), "AIRCONNECT - ADMIN CONSOLE")
    paragraph(
        draw,
        (64, 168),
        "작은 캡처 여러 개 대신, 운영자가 실제로 무엇을 보는지 드러나는 큰 화면 두 장으로 다시 배치했습니다.",
        1180,
        36,
        bold=True,
        line_gap=12,
    )
    dashboard = make_dashboard_mock()
    consistency = make_consistency_mock()

    draw_screen_card(draw, image, (64, 276, 770, 800), "정합성 점검 화면")
    inner1 = ImageOps.contain(consistency, (770 - 64 - 64, 800 - 276 - 92))
    image.paste(inner1, (96, 352))
    draw_screen_card(draw, image, (830, 276, 1536, 800), "Audit Log / API 통계 화면")
    inner2 = ImageOps.contain(dashboard, (1536 - 830 - 64, 800 - 276 - 92))
    image.paste(inner2, (862, 352))
    footer_note(draw, "Admin Console은 정합성 점검 / Audit Log / API 통계처럼 운영 이슈를 추적하는 화면을 중심으로 제시했습니다.")
    return image


def slide_09():
    image, draw = page_scaffold(9, "PROJECT 02 MoneyWay")
    pill(draw, (64, 108), "PROJECT 02")
    paragraph(
        draw,
        (64, 168),
        "MoneyWay는 제주 여행 조건을 입력하면 숙소 / 예산 / 거리 기준으로 AI 여행 일정을 생성하는 서비스입니다.",
        1240,
        38,
        bold=True,
        line_gap=12,
    )
    info_box(draw, (64, 300, 760, 760), "문제", "AI 일정은 그럴듯해 보여도 실제 DB에 없는 장소를 만들거나 예산을 넘길 수 있습니다.\n실제 시연 가능한 결과를 만들려면 생성 전후 제약과 검증이 필요했습니다.")
    info_box(draw, (790, 300, 1536, 760), "핵심 결과", "우수상 / 한국관광공사 2025 공모전\nmock 모드 p95 68ms -> 36ms / 47.1% 개선\n30개 AI 오류 변형 테스트에서 0건 저장")
    add_badges(draw, 64, 792, ["TourAPI", "OpenAI API", "Validation Pipeline", "Performance"])
    footer_note(draw, "AirConnect 다음 사례로는 AI 출력의 신뢰성을 높인 MoneyWay를 유지했습니다.")
    return image


def slide_10():
    image, draw = page_scaffold(10, "CASE 04")
    pill(draw, (64, 108), "CASE 04")
    paragraph(
        draw,
        (64, 168),
        "AI 응답 지연을 감으로 판단하지 않고 계측으로 서버 내부 병목과 외부 API 병목을 분리했습니다.",
        1180,
        38,
        bold=True,
        line_gap=12,
    )
    rounded(draw, (64, 290, 780, 788), radius=30, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (92, 322), "계측 지점", 28, bold=True)
    points = [
        "전체 생성 시간",
        "장소 후보 조회",
        "거리 필터링",
        "OpenAI 호출 (real mode)",
        "JSON 파싱 / Plan 저장",
        "dev / local 전용 테스트 endpoint + PERF 로그 태그",
    ]
    yy = 382
    for p in points:
        paragraph(draw, (98, yy), f"• {p}", 620, 22, bold=True, line_gap=8)
        yy += 54
    rounded(draw, (830, 290, 1536, 788), radius=30, fill=PANEL, outline=BORDER, width=2)
    draw_text(draw, (858, 322), "측정 결과", 28, bold=True)
    rounded(draw, (858, 382, 1508, 520), radius=24, fill=WHITE, outline=BORDER, width=1)
    draw_text(draw, (1183, 418), "MOCK MODE", 24, bold=True, fill=BLUE, anchor="mm")
    draw_text(draw, (1183, 468), "p95 68ms -> 36ms", 40, bold=True, anchor="mm")
    draw_text(draw, (1183, 506), "서버 내부 처리 기준 47.1% 개선", 20, bold=True, fill=MUTED, anchor="mm")
    rounded(draw, (858, 554, 1508, 716), radius=24, fill=WHITE, outline=BORDER, width=1)
    draw_text(draw, (1183, 590), "REAL MODE", 24, bold=True, fill=GREEN, anchor="mm")
    paragraph(draw, (922, 632), "전체 체감 응답의 대부분이 OpenAI 호출에서 발생했고, 병목이 서버 내부 로직이 아니라 외부 API라는 점을 수치로 분리했습니다.", 520, 22, bold=True, line_gap=8)
    footer_note(draw, "측정 포인트 / mock / real 모드를 나눠 무엇을 개선했고 무엇이 외부 병목인지 설명하도록 구성했습니다.")
    return image


def slide_11():
    image, draw = page_scaffold(11, "CASE 05")
    pill(draw, (64, 108), "CASE 05")
    paragraph(
        draw,
        (64, 168),
        "AI 출력을 후처리로 고치는 대신 잘못된 결과가 생성될 가능성 자체를 줄였습니다.",
        1160,
        38,
        bold=True,
        line_gap=12,
    )
    steps = [
        ("① 입력 제약", "DB 후보 제한 / 10km 시작 -> 1.5배 확장 / 예산 선필터"),
        ("② AI 생성", "DB 후보 장소만 슬롯에 배치 / 슬롯 수 지정"),
        ("③ 응답 검증", "JSON 파싱 / 슬롯 누락 / 예산 초과 / DB 외 장소 / 카테고리 불일치"),
        ("④ 저장", "검증 통과 결과만 Plan + PlanPlace로 저장"),
    ]
    sx = 64
    for title, body in steps:
        rounded(draw, (sx, 326, sx + 360, 730), radius=28, fill=PANEL, outline=BORDER, width=2)
        draw_text(draw, (sx + 26, 360), title, 28, bold=True)
        paragraph(draw, (sx + 26, 424), body, 308, 22, bold=True, line_gap=10)
        sx += 382
    rounded(draw, (64, 764, 1536, 816), radius=20, fill="#E8F7EF", outline="#B2D8C2", width=1)
    draw_text(draw, (88, 781), "결과: mock 기반 30개 AI 오류 응답 변형 테스트에서 DB 외 장소 생성 / 슬롯 누락 / 예산 초과 / 카테고리 불일치가 모두 0건이었습니다.", 20, bold=True, fill=GREEN)
    footer_note(draw, "생성 전 제약과 생성 후 검증을 분리해 '왜 믿을 수 있는지'가 드러나도록 정리했습니다.")
    return image


def slide_12():
    image, draw = page_scaffold(12, "CLOSING")
    pill(draw, (64, 108), "CLOSING")
    paragraph(
        draw,
        (64, 190),
        "프로젝트를 통해 서버가 만들어내는 결과는 항상 검증 가능해야 한다는 것을 배웠습니다.",
        1320,
        50,
        bold=True,
        line_gap=14,
    )
    paragraph(
        draw,
        (64, 350),
        "결제와 광고 보상에서는 같은 요청이 반복되어도 재화 상태가 한 번만 바뀌어야 했고,\nAI 일정 생성에서는 자연스러운 응답보다 실제 DB와 예산 안에서 검증 가능한 결과가 더 중요했습니다.\n\n앞으로도 상태 변경의 정확성이 중요한 도메인에서 문제를 빠르게 이해하고,\n필요한 영역을 배워 팀의 결과로 연결하는 백엔드 엔지니어로 성장하고 싶습니다.",
        1220,
        28,
        bold=True,
        line_gap=14,
    )
    draw_text(draw, (64, 736), "감사합니다.", 34, bold=True)
    draw_text(draw, (64, 784), "이강현 / Lee Kanghyun", 24, bold=True, fill=MUTED)
    footer_note(draw, "개정본에서는 특히 AirConnect 4~8페이지를 검증 흐름 중심으로 재설계했습니다.")
    return image


SLIDE_BUILDERS = [
    slide_01,
    slide_02,
    slide_03,
    slide_04,
    slide_05,
    slide_06,
    slide_07,
    slide_08,
    slide_09,
    slide_10,
    slide_11,
    slide_12,
]


def build():
    ensure_dirs()
    images: list[Image.Image] = []
    for idx, builder in enumerate(SLIDE_BUILDERS, start=1):
        image = builder()
        image.save(SLIDES_DIR / f"slide-{idx:02d}.png")
        images.append(image.convert("RGB"))
    images[0].save(PDF_PATH, save_all=True, append_images=images[1:], resolution=150.0)
    return PDF_PATH


if __name__ == "__main__":
    out = build()
    print(out)
