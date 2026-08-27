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

    locations = []
    items = []
    rules = []
    for recommendation in recommendations:
        location_id = str(uuid.uuid5(uuid.UUID(recommendation["id"]), "local-result"))
        name = recommendation["flower_name"]["ko"]
        meaning = recommendation["flower_meaning"]["ko"]
        locations.append({
            "id": location_id,
            "code": f"FLOWER-{recommendation['display_order']:03d}",
            "name": {"ko": name},
            "description": {"ko": "클라이언트 단독 꽃 추천 결과"},
            "image_url": None,
            "capacity": None,
            "status": "NORMAL",
            "marker": {"x_percent": 50.0, "y_percent": 50.0},
            "active": recommendation["active"],
        })
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
        "config_version": 2,
        "minimum_app_version": 1,
        "project_code": catalog["project_code"],
        "default_language": "ko",
        "supported_languages": ["ko"],
        "theme": {
            "name": {"ko": "AI 감정상태별 꽃 추천"},
            "logo_url": None,
            "primary_color": "#6A5ACD",
            "background_image_url": "",
            "map_image_url": "",
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
