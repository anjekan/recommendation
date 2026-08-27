#!/usr/bin/env python3
"""Convert the approved flower recommendation PDF table to canonical JSON."""

import argparse
import json
import re
import uuid
from pathlib import Path

import pdfplumber


EMOTION_CODES = {
    "평안함": "SERENITY",
    "안정": "STABILITY",
    "느긋함": "RELAXED",
    "즐거움": "JOY",
    "차분함": "CALM",
    "활력": "VITALITY",
    "집중": "FOCUS",
    "몰입": "IMMERSION",
    "고조": "ELEVATION",
    "열정": "PASSION",
}
NAMESPACE = uuid.UUID("7de569d0-56b9-4cac-b7f8-81e2adcb9c63")


def parse_stress_range(value: str) -> tuple[int, int]:
    numbers = [int(number) for number in re.findall(r"\d+", value)]
    if "이상" in value and len(numbers) == 1:
        return numbers[0], 100
    if len(numbers) == 2:
        return numbers[0], numbers[1]
    raise ValueError(f"Unsupported stress range: {value}")


def clean(value: str | None) -> str:
    return " ".join((value or "").split())


def import_pdf(input_path: Path) -> dict:
    rows: list[list[str | None]] = []
    with pdfplumber.open(input_path) as pdf:
        for page in pdf.pages:
            tables = page.extract_tables()
            if len(tables) != 1:
                raise ValueError(f"Expected one table on page {page.page_number}, found {len(tables)}")
            page_rows = tables[0]
            if page.page_number == 1:
                header = [clean(value) for value in page_rows.pop(0)]
                if header != ["No.", "감정상태", "스트레스 구간", "추천꽃", "꽃말", "출력 메시지", "비고 (매칭 근거)"]:
                    raise ValueError(f"Unexpected PDF columns: {header}")
            rows.extend(page_rows)

    recommendations = []
    for raw in rows:
        number, emotion, stress_range, flower, meaning, message, rationale = map(clean, raw)
        stress_min, stress_max = parse_stress_range(stress_range)
        emotion_code = EMOTION_CODES.get(emotion)
        if emotion_code is None:
            raise ValueError(f"Unknown emotion at row {number}: {emotion}")
        recommendations.append(
            {
                "id": str(uuid.uuid5(NAMESPACE, f"{number}:{emotion}:{flower}")),
                "display_order": int(number),
                "emotion_code": emotion_code,
                "emotion_name": {"ko": emotion},
                "stress_min": stress_min,
                "stress_max": stress_max,
                "flower_name": {"ko": flower},
                "flower_meaning": {"ko": meaning},
                "output_message": {"ko": message},
                "match_rationale": {"ko": rationale},
                "active": True,
            }
        )

    orders = [item["display_order"] for item in recommendations]
    if orders != list(range(1, 84)):
        raise ValueError(f"Expected rows 1..83, found {orders}")
    return {
        "schema_version": 1,
        "project_code": "TAEAN_FLOWER_2026",
        "source_document": input_path.name,
        "recommendations": recommendations,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_pdf", type=Path)
    parser.add_argument("output_json", type=Path)
    args = parser.parse_args()
    result = import_pdf(args.input_pdf)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(result['recommendations'])} recommendations to {args.output_json}")


if __name__ == "__main__":
    main()
