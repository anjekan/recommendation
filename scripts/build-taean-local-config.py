#!/usr/bin/env python3
"""Build the Android LOCAL-mode project config from the flower catalog."""

import argparse
import json
import uuid
from pathlib import Path


COLORS = {
    "SERENITY": "#7CB342", "STABILITY": "#26A69A", "RELAXED": "#FFB74D",
    "JOY": "#EC407A", "CALM": "#5C6BC0", "VITALITY": "#FDD835",
    "FOCUS": "#29B6F6", "IMMERSION": "#AB47BC", "ELEVATION": "#FF7043",
    "PASSION": "#EF5350",
}

GARDENS = [
    ("butterfly", "나비 정원", 24.1, 80.4),
    ("mirror", "마음을 비추는 정원", 48.0, 66.2),
    ("greeting", "꽃의 인사", 31.0, 62.7),
    ("emotion", "감정의 정원", 28.6, 45.4),
    ("scent", "향기 정원", 22.1, 32.6),
    ("rest", "안식의 정원", 40.9, 21.6),
    ("herb", "약초 정원", 52.0, 25.5),
    ("harmony", "AI 하모니 가든", 65.2, 38.7),
    ("connect", "이음 정원", 60.5, 53.9),
    ("sunlight", "햇살파동 정원", 73.2, 57.0),
    ("future", "희망미래 정원", 65.6, 80.9),
    ("healing", "꽃잠의 정원", 16.5, 50.7),
    ("wave", "물결정원", 16.5, 28.0),
    ("square", "광장정원", 46.9, 44.0),
]

EMOTION_GARDENS = {
    "SERENITY": ["connect", "herb"],
    "STABILITY": ["butterfly", "healing", "rest"],
    "RELAXED": ["emotion", "healing", "rest"],
    "JOY": ["square", "future", "greeting", "sunlight"],
    "CALM": ["butterfly", "connect", "rest", "greeting"],
    "VITALITY": ["scent", "sunlight", "wave"],
    "FOCUS": ["mirror", "healing", "harmony", "connect"],
    "IMMERSION": ["harmony", "greeting", "wave"],
    "ELEVATION": ["scent", "greeting", "wave"],
    "PASSION": ["rest", "herb", "sunlight"],
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    recommendations = catalog["recommendations"]
    by_emotion = {}
    for recommendation in recommendations:
        by_emotion.setdefault(recommendation["emotion_code"], recommendation)

    namespace = uuid.uuid5(uuid.NAMESPACE_DNS, "recommendation.projects.taean")
    location_ids = {code: str(uuid.uuid5(namespace, f"location:{code}")) for code, _, _, _ in GARDENS}
    locations = [
        {
            "id": location_ids[code], "code": code.upper(), "name": {"ko": name},
            "description": {"ko": "태안 프로젝트 추천 장소"}, "image_url": None,
            "capacity": None, "status": "NORMAL",
            "marker": {"x_percent": x, "y_percent": y}, "active": True,
        }
        for code, name, x, y in GARDENS
    ]
    items = []
    rules = []
    for recommendation in recommendations:
        candidates = EMOTION_GARDENS[recommendation["emotion_code"]]
        garden_code = candidates[(recommendation["display_order"] - 1) % len(candidates)]
        location_id = location_ids[garden_code]
        name = recommendation["flower_name"]["ko"]
        meaning = recommendation["flower_meaning"]["ko"]
        items.append({
            "id": recommendation["id"],
            "type": "flower",
            "location_id": location_id,
            "name": {"ko": f"{name} · {meaning}"},
            "description": {"ko": recommendation["match_rationale"]["ko"]},
            "image_url": "",
            "attributes": {
                "stress_min": recommendation["stress_min"],
                "stress_max": recommendation["stress_max"],
                "output_message": recommendation["output_message"]["ko"],
            },
            "active": recommendation["active"],
        })
        rules.append({
            "emotion_code": recommendation["emotion_code"],
            "item_id": recommendation["id"],
            "weight": 100,
            "priority": 10,
            "active": recommendation["active"],
        })

    config = {
        "schema_version": 1,
        "config_version": 3,
        "minimum_app_version": 1,
        "project_code": "taean",
        "default_language": "ko",
        "supported_languages": ["ko"],
        "theme": {
            "name": {"ko": "AI 감정상태별 꽃 추천"},
            "logo_url": None,
            "primary_color": "#6A5ACD",
            "background_image_url": "",
            "map_image_url": "android.resource://kr.co.ninetyseconds.recommendation/drawable/taean_garden_map",
        },
        "emotion_profiles": [
            {
                "code": code,
                "name": source["emotion_name"],
                "message": source["output_message"],
                "color": COLORS[code],
                "icon": code.lower(),
                "active": True,
            }
            for code, source in by_emotion.items()
        ],
        "analysis_mappings": [
            {"source_label": "Neutral", "emotion_code": "SERENITY"},
            {"source_label": "Happy", "emotion_code": "JOY"},
            {"source_label": "Surprise", "emotion_code": "VITALITY"},
            {"source_label": "Sad", "emotion_code": "STABILITY"},
            {"source_label": "Anger", "emotion_code": "ELEVATION"},
            {"source_label": "Disgust", "emotion_code": "CALM"},
            {"source_label": "Fear", "emotion_code": "STABILITY"},
            {"source_label": "Contempt", "emotion_code": "IMMERSION"},
        ],
        "locations": locations,
        "items": items,
        "rules": rules,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Built LOCAL config with {len(items)} flowers")


if __name__ == "__main__":
    main()
