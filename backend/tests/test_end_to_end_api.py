from fastapi.testclient import TestClient


def test_full_mobile_escalate_human_review_audit_and_mobile_verify_flow(client: TestClient) -> None:
    # 1. Fake Away mobile escalates a suspicious Arabic claim
    esc = client.post(
        "/api/mobile/escalate",
        json={
            "text": "الإنفجار في وسط الموصل اليوم سبب إغلاق الجسر القديم",
            "language": "ar",
            "offline_risk_score": 0.78,
            "scan_type": "SCREEN",
            "summary": "High clickbait risk on Facebook post",
        },
    )
    assert esc.status_code == 200
    claim_id = esc.json()["claim_id"]
    assert esc.json()["status"] == "in_review"

    # 2. Verify mobile metadata appears in Newsroom queue
    claims_res = client.get("/api/claims")
    assert claims_res.status_code == 200
    claim_row = next(c for c in claims_res.json()["claims"] if c["id"] == claim_id)
    assert claim_row["source_app"] == "fake_away_android"
    assert claim_row["mobile_risk_score"] == 0.78

    # 3. Journalist attaches verified evidence URL
    ev = client.post(
        f"/api/claims/{claim_id}/evidence",
        json={
            "label": "بيان قيادة عمليات نينوى الرسمي",
            "url": "https://ninawa.gov.iq/statements/104",
            "publisher": "محافظة نينوى",
        },
    )
    assert ev.status_code == 200

    # 4. Reviewer issues authoritative verdict with X-API-Key
    rev = client.patch(
        f"/api/claims/{claim_id}/review",
        headers={"X-API-Key": "test-secret-key"},
        json={
            "status": "false",
            "reviewer": "د. سارة العراقي",
            "rationale": "الصورة قديمة من 2021 ولا يوجد أي إغلاق للجسر اليوم بحسب البيان الرسمي.",
        },
    )
    assert rev.status_code == 200
    assert rev.json()["status"] == "false"

    # 5. Audit trail endpoint exposes complete lifecycle
    audit_res = client.get(f"/api/claims/{claim_id}/audit")
    assert audit_res.status_code == 200
    actions = [a["action"] for a in audit_res.json()["audits"]]
    assert actions == ["mobile_escalated", "evidence_added", "reviewed"]

    # 6. Another Fake Away phone queries slightly differently phrased Arabic claim -> matches reviewed verdict!
    verify_res = client.post(
        "/api/mobile/verify",
        json={
            "text": "انفجار وسط موصل اليوم أغلق الجسر القديم",
            "language": "ar",
        },
    )
    assert verify_res.status_code == 200
    body = verify_res.json()
    assert body["matched_claim"] is not None
    assert body["matched_claim"]["id"] == claim_id
    assert body["matched_claim"]["status"] == "false"
    assert body["matched_claim"]["reviewer"] == "د. سارة العراقي"
    assert body["matched_claim"]["similarity"] >= 0.30
    assert body["matched_claim"]["evidence"][0]["url"] == "https://ninawa.gov.iq/statements/104"

    # 7. Stats reflect mobile escalation count
    stats_res = client.get("/api/stats")
    assert stats_res.status_code == 200
    assert stats_res.json()["mobile_escalations"] == 1
