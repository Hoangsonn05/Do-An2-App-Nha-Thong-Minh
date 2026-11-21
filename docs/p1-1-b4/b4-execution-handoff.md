# P1.1-B4 — Execution Handoff Manifest (Branch A → Branch B)

- **Timestamp (Local):** 2026-11-21 08:42:00 +07:00
- **Repository:** `d:\MMO\DoAn2\app-nha-thong-minh`
- **Git Baseline Checkpoint:** `e7a805c` (`checkpoint task  b3`)
- **Branch A Status:** **COMPLETE (DESIGN-ONLY)** — Ready for Independent Review by Branch B (Test Model)
- **Target Validation Output for Branch B:** `docs/p1-1-b4/b4-independent-validation-report.md` *(not created in Branch A)*

---

## 1. Những file đã đọc (Read Inventory)

### A. Tài liệu kiến trúc & báo cáo các phase trước
- `docs/P1.1-B0_Cloud_Database_Architecture_Decision.md` (RTDB-only rev.2)
- `docs/P1.1-B1_RTDB_Cloud_Foundation_Plan.md`
- `docs/p1-1-b1/b1-real-world-verification-report.md` (`B1 CONDITIONAL PASS`)
- `docs/p1-1-b1/rules-test-report.txt` (lines 1–60)
- `docs/p1-1-b2/b2-firebase-configuration-readiness-report.md` (`B2 PASS`)
- `docs/p1-1-b3/b3-cloud-session-multi-home-member-report.md` (`B3 PASS`)
- `docs/VALIDATION.md`

### B. Source Code Cloud (`app/src/main/java/com/example/smarthomeai/cloud/`)
- `CloudModels.kt`
- `CloudRepository.kt`
- `CloudSession.kt`
- `FirebaseProvider.kt`
- `RtdbPaths.kt`
- `RtdbPayloadBuilder.kt`
- `CloudErrorMapper.kt`
- `RtdbHomeMerger.kt`
- `RtdbCloudRepository.kt`
- `CloudHomeManager.kt`

### C. Source Code UI, ViewModel, Auth & Cấu hình Android
- `app/src/main/java/com/example/smarthomeai/ui/components/CloudHomeSection.kt`
- `app/src/main/java/com/example/smarthomeai/ui/screens/HomeScreen.kt` (lines 1–80)
- `app/src/main/java/com/example/smarthomeai/ui/screens/SettingsScreen.kt` (lines 1–129)
- `app/src/main/java/com/example/smarthomeai/ui/theme/Theme.kt`
- `app/src/main/java/com/example/smarthomeai/data/SmartHomeViewModel.kt` (lines 1–120)
- `app/src/main/java/com/example/smarthomeai/data/AuthRepository.kt`
- `app/src/main/java/com/example/smarthomeai/MainActivity.kt` (lines 1–99)
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

### D. Firebase RTDB Rules & Cấu hình Emulator (`cloud-rules/`)
- `cloud-rules/database.rules.src.json`
- `cloud-rules/build-rules.mjs`
- `cloud-rules/firebase.json`
- `cloud-rules/package.json`
- `cloud-rules/test/rules.test.mjs` (lines 1–60)

---

## 2. Những file đã tạo / chỉnh sửa (Branch A Diff)

### File mới được tạo (chỉ tài liệu thiết kế B4)
1. `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` (Tài liệu thiết kế đầy đủ 19 mục)
2. `docs/p1-1-b4/b4-execution-handoff.md` (Tài liệu bàn giao này)

### File chỉnh sửa
- **Không có (0 tracked files modified).**

---

## 3. Tóm tắt các quyết định thiết kế đã chốt

1. **RTDB-only Database + Trusted Backend Boundary:** Tiếp tục dùng duy nhất Firebase RTDB (`appaintm`, `asia-southeast1`), nhưng bắt buộc có **Trusted Backend** (Firebase Cloud Functions v2 tại `asia-southeast1` [Option T1] hoặc HTTPS Admin SDK Service [Option T2]) xử lý `createInvitation`, `revokeInvitation`, `validateInvitation`, và `joinHome`.
2. **Split Dual-Node Schema:**
   - `homes/{homeId}/invitations/{invitationId}` (`.read: "@AUTH && @OWNER"`, `.write: false`) chứa metadata lời mời & `redemptions/{uid}`; **tuyệt đối không chứa `token` hay `tokenHash`**.
   - `invitationTokens/{tokenHash}` (`.read: false, .write: false`) với key là `HMAC-SHA256(serverPepper, rawToken)` làm bảng tra cứu $O(1)$ thuần server trỏ tới `{ homeId, invitationId, createdAt, expiresAt }`.
   - `invitationRateLimits/{uid}` (`.read: false, .write: false`) để chống brute-force / spam.
3. **Data Model & State Machine:**
   - Cấp cố định `role = "member"`; `usageLimit` mặc định `1` (trong `[1, 10]`); `ttlMs` trong `[5 phút, 7 ngày]` (mặc định QR `15 phút`).
   - Phân tách **Persisted Status** (`ACTIVE`, `REVOKED`, `EXHAUSTED`) vs **Derived Status** (`EXPIRED` theo `serverNow >= expiresAt`, `INVALID` khi thiếu Home / đổi Owner / token sai).
   - Mọi thao tác `validate` và `join` phải kiểm tra `homes/{homeId}/meta` tồn tại và `meta.ownerUid == createdByUid`.
4. **Concurrency & Idempotency (2-Phase Atomic Join):**
   - **Pha 1:** RTDB `transaction()` trên `homes/{homeId}/invitations/{invitationId}` tăng `usedCount`, chuyển `EXHAUSTED` nếu đạt ngưỡng, ghi `redemptions/{uid} = { state: "CLAIMED", attemptId, claimedAt }`. Chặn replay nếu thành viên đã bị xóa cố quét lại mã đa-lượt cũ (`redemptions/{uid}` đã tồn tại).
   - **Pha 2:** Root multi-path atomic `update()` ghi đồng thời `homes/{h}/members/{uid}`, `userHomes/{uid}/{h} = "member"`, và `redemptions/{uid}/state = "COMPLETED"`. Retry cùng `attemptId` khôi phục `CLAIMED` hoặc trả idempotent `Ok` khi đã `COMPLETED`.
5. **QR Contract v1:** Payload JSON trực tiếp cho in-app scanner: `{"v":1,"type":"home_invite","token":"<43-char-base64url-256bit-csprng>"}`. Không chứa `homeId`, `deviceKey`, mật khẩu, hay Firebase Auth token.
6. **Kotlin & UI Contract:** Tạo interface thuần Kotlin `CloudInvitationRepository` tách khỏi `CloudRepository` (tránh phá test B1–B3), thêm `QrInvitationParser`, mở rộng `CloudError` backward-compatible, tích hợp `CloudHomeManager` với `sessionGen`/`cancelAndJoin()`/`pendingJoinedHomeId`. UI sử dụng `FLAG_SECURE`, `EXTRA_IS_SENSITIVE`, CameraX + fallback nhập tay, không bao giờ ẩn/khóa điều khiển LAN ESP32.

---

## 4. Các phương án đã loại bỏ và lý do

| Phương án bị loại bỏ | Lý do kỹ thuật loại bỏ |
|---|---|
| **Pure Client + RTDB Security Rules** (không có Trusted Backend) | RTDB Rules không có HMAC/SHA-256 để băm token; không hỗ trợ rate limiting; không thể cho Stranger đọc preview `homes/{h}/meta/name` khi chưa là Member mà không mở `.read` trái phép; `members/$uid` hiện yêu cầu `addedBy === auth.uid` của `@OWNER`; Rules không kiểm soát được đồng bộ nguyên tử giữa `usedCount`, `members`, và `userHomes` (`L3`/`L5`); lỗi đều trả `PERMISSION_DENIED` làm mất ngữ nghĩa lỗi nghiệp vụ. |
| **Lưu tất cả chỉ dưới `homes/{homeId}/invitations/{id}`** | Người nhận quét QR không thể tra cứu $O(1)$ bằng token trừ khi lộ `homeId` vào QR hoặc quét toàn bộ cây `homes` (vi phạm Rules `L4`). |
| **Lưu tất cả chỉ ở nhánh toàn cục `invitations/{id}`** | Owner không thể `observeInvitations(homeId)` bằng RTDB Rules an toàn vì Rules không phải filter (`L4`). |
| **Lưu plaintext `token` hoặc lưu `tokenHash` trong `homes/{h}/invitations`** | Rò rỉ trực tiếp token hoặc cho phép Owner đọc hash dẫn tới bề mặt tấn công không cần thiết. |
| **HTTPS Deep Link / Custom URI Scheme ở QR v1** | Phụ thuộc domain/assetlinks, làm rò rỉ token qua URL query / browser history / Intent hijacking, và buộc sửa `MainActivity.kt` (`LOCKED`) & `AndroidManifest.xml`. |
| **Cho phép cấp quyền `OWNER` hoặc đưa `deviceKey` vào QR** | Vi phạm mô hình single-owner (`B0 §8.2`), rủi ro chiếm quyền nhà; đưa `deviceKey` lên Cloud/QR vi phạm trực tiếp yêu cầu B4.3 và `B0 §8.5`. |

---

## 5. Các rủi ro còn tồn tại

1. **Chụp màn hình vật lý bằng camera ngoài:** `FLAG_SECURE` không chặn được chụp ảnh màn hình bằng điện thoại khác (giảm thiểu bằng `usageLimit=1`, TTL 15 phút, revoke 1 chạm).
2. **Ràng buộc Admin SDK:** Firebase Admin SDK bỏ qua toàn bộ RTDB Rules & `.validate`; chất lượng bảo mật phụ thuộc hoàn toàn vào validation trong code Trusted Backend và bộ test Emulator/Integration ở phase triển khai.
3. **Chi phí / Hạ tầng Backend:** Option T1 cần Blaze + Budget Alert; Option T2 cần server HTTPS tự vận hành và quản lý Secret.
4. **Rác dữ liệu hết hạn/thu hồi:** RTDB không có TTL tự động, cần cơ chế dọn rác (đưa vào `UD-3`).
5. **Điều kiện tiên quyết giữ nguyên:** B1 `A2` physical HW vẫn `PENDING USER VERIFICATION`; `B1-D` vẫn `LOCKED`.

---

## 6. Các giả định cần Test Model (Branch B) kiểm tra độc lập

1. **Admin SDK vs RTDB Rules:** Kiểm tra xem thiết kế có vô tình giả định `.validate` trong Rules sẽ bắt lỗi ghi từ Trusted Backend hay chưa (trong báo cáo §9.4 đã cảnh báo rõ Admin SDK bypasses Rules — Branch B cần xác minh tính nhất quán xuyên suốt các mục).
2. **RTDB `transaction(null)` behavior:** Kiểm tra giả định trong §10.2 về lượt gọi lạc quan `currentData === null` của RTDB SDK `transaction()` (tránh abort sớm bằng `undefined` trên `null`).
3. **Khôi phục 2 pha (`CLAIMED -> COMPLETED`):** Xác minh có tồn tại trạng thái treo hoặc điều kiện race nào (ví dụ: Home bị xóa giữa Pha 1 và Pha 2, Owner revoke đồng thời, hoặc 1 UID quét 2 mã mời *khác nhau* của cùng 1 Home song song) làm lệch `usedCount`, `members`, hoặc `userHomes`.
4. **Tương thích `CloudError` và `CloudHomeManager`:** Xác minh `CloudHomeManager.describeActionError` (hiện dùng `when (error)` không có nhánh `else` ở dòng 684–693) và `CloudRepository` 5 hàm hiện có trong source; xác nhận thiết kế B4 không gây breaking change ngầm với B3.
5. **Tách biệt `tokenHash` khỏi Owner:** Xác minh `homes/{homeId}/invitations/{invitationId}` thực sự không chứa bất kỳ `token` hoặc `tokenHash` nào và Owner vẫn quản lý/thu hồi đầy đủ qua `invitationId`.
6. **Sai khác tài liệu `B0 §10` vs `B4`:** Xác minh việc từ chối `deviceKey` trong QR B4 là đúng với yêu cầu B4.3 và nguyên tắc `B0 §2 / §6.1 / §8.5`, dù `B0 §10` dòng 744 từng ghi chú khác ở giai đoạn lộ trình cũ.
7. **State Machine completeness:** Kiểm tra mọi trạng thái (`ACTIVE`, `REVOKED`, `EXHAUSTED`, `EXPIRED`, `INVALID`) xem có deadlock, trạng thái không thể đạt hoặc không thể thoát không.

---

## 7. Các câu hỏi cần người dùng quyết định (`UD-1`..`UD-5`)

Chi tiết tại `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` §16.2:
1. **UD-1:** Chọn Option T1 (Cloud Functions v2 `asia-southeast1` + Blaze + Budget Alert) hay Option T2 (Self-hosted HTTPS Admin SDK trên RTDB Spark)?
2. **UD-2:** UI v1 khóa cứng `usageLimit = 1` hay cho phép Owner chọn `1..10`?
3. **UD-3:** Chính sách dọn bản ghi hết hạn/thu hồi (tự động > 30 ngày kèm `invitationTokenIndex` hay thủ công bởi Owner)?
4. **UD-4:** Phê duyệt thêm `mlkit:barcode-scanning` và `zxing:core` ở phase Implementation tương lai?
5. **UD-5:** Phê duyệt mở khóa phạm vi cho phase Implementation riêng sau khi B4 hoàn tất?

---

## 8. Đường dẫn & Vị trí nội dung quan trọng

| Nội dung | Vị trí trong `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md` |
|---|---|
| Executive Summary & Kiến trúc tổng thể | §1, §3 |
| Đối chiếu source B1–B3 & Rules hiện tại | §2 |
| Invitation Data Model & 12 câu hỏi B4.1 | §4 (§4.1, §4.2, §4.3) |
| State Machine (`stateDiagram-v2` & thứ tự ưu tiên) | §5 |
| Join Home Flow (12 bước + 15 ca biên/lỗi) | §6 (§6.1, §6.2, §6.3) |
| QR Contract v1 & Danh sách cấm | §7 (§7.1–§7.4) |
| RTDB Schema Decision (So sánh 3 hướng + Cây chốt) | §8 (§8.1–§8.3) |
| Security Rules Design (12 câu hỏi + Ma trận + JSON Proposal) | §9 (§9.1–§9.4) |
| Transaction / Backend Boundary & Giao thức 2 pha | §10 (§10.1–§10.3) |
| Threat Model (16 threats x 7 cột) | §11 |
| Kotlin API Contract (6 API x 11 thuộc tính) | §12 (§12.1–§12.3) |
| UI / UX Contract (Quyền UI + Flows + Accessibility) | §13 (§13.1–§13.2) |
| Error Mapping | §14 (§14.1–§14.2) |
| Future Implementation Test Matrix | §15 (§15.1–§15.5) |
| Risks, Open Decisions (`UD-1..UD-5`) & Final Decision | §16, §17 |
| Scope Compliance & References | §18, §19 |

---

## 9. Xác nhận tuân thủ giới hạn Branch A

- **Xác nhận không sửa source implementation:** **CONFIRMED** — 0 file `.kt`, `build.gradle.kts`, `AndroidManifest.xml`, `esp32/**`, hoặc `app/google-services.json` bị sửa hay thêm mới.
- **Xác nhận không sửa Firebase Rules:** **CONFIRMED** — `cloud-rules/database.rules.src.json`, `cloud-rules/database.rules.json`, `build-rules.mjs`, và `cloud-rules/test/*` hoàn toàn nguyên vẹn.
- **Xác nhận không chạy test/build/emulator:** **CONFIRMED** — Không chạy Gradle, JUnit, AndroidTest, `npm test`, Firebase Emulator, benchmark hay workload kiểm thử nào.
- **Xác nhận không chạm Production & giữ nguyên LOCK:** **CONFIRMED** — Không truy cập `appaintm` Console/RTDB; `B1-D` giữ nguyên `LOCKED`.

---

## 10. Hướng dẫn cho Branch B (Test Model)

Khi người dùng chuyển sang **Test Model** trong chính cuộc hội thoại này, Branch B cần:
1. Đọc lại prompt P1.1-B4 trong lịch sử hội thoại, đọc `docs/p1-1-b4/b4-execution-handoff.md` và `docs/p1-1-b4/b4-invitation-join-qr-contract-report.md`.
2. Kiểm tra `git status --short` / `git diff` để xác nhận Branch A không sửa source hoặc Rules.
3. Đối chiếu độc lập mọi symbol Kotlin, đường dẫn RTDB, Security Rules, và trạng thái B0–B3 được trích dẫn với source thực tế trong repo.
4. Thực hiện review độc lập theo đủ 7 nhóm (A. Architecture, B. Data Model, C. Security, D. Concurrency & Consistency, E. QR, F. API/UI, G. Scope & Artifacts).
5. Phân loại mọi finding (nếu có) với đầy đủ ID, Severity, bằng chứng, vị trí, tác động, hướng khắc phục, phân biệt Confirmed Finding / Potential Risk / Missing Evidence / User Decision Required.
6. Xuất báo cáo review độc lập tại `docs/p1-1-b4/b4-independent-validation-report.md` (gồm đủ 13 mục theo Section IX) và đưa ra kết luận cuối cùng (`PASS` / `CONDITIONAL PASS` / `FAIL` / `BLOCKED`).
