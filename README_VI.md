# TaiXiu 3.0

[**Tiếng Việt**](README_VI.md) | [English](README.md)

[![Build](https://github.com/Alexteens24/TaiXiu/actions/workflows/build.yml/badge.svg)](https://github.com/Alexteens24/TaiXiu/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper 1.21.4+](https://img.shields.io/badge/Paper-1.21.4%2B-blue.svg)](https://papermc.io/)
[![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-green.svg)](LICENSE)
[![Tài liệu](https://img.shields.io/badge/Tài%20liệu-GitHub%20Pages-e34b4b.svg)](https://alexteens24.github.io/TaiXiu/)

Fork hiện đại hóa plugin Tài Xỉu cho Paper/Folia, tập trung vào an toàn giao dịch economy, lưu trữ SQLite và khả năng phục hồi sau crash.

Fork này được duy trì tại [Alexteens24/TaiXiu](https://github.com/Alexteens24/TaiXiu), dựa trên dự án gốc [CortezRomeo/TaiXiu](https://github.com/CortezRomeo/TaiXiu).

> [!IMPORTANT]
> Nhánh 3.0 hiện đang trong giai đoạn kiểm thử trước release. Build và unit test đã chạy thành công, nhưng checklist với economy provider thật trên Paper/Folia phải được hoàn thành trước khi dùng trên server production.

> [!WARNING]
> Đây là plugin có cơ chế đặt cược bằng tiền/điểm trong game. Người vận hành server tự chịu trách nhiệm về quy định, điều khoản và pháp luật áp dụng tại khu vực của mình.

## Luật chơi

Hệ thống tung ba xúc xắc sáu mặt:

- Tổng từ `4–10`: Xỉu.
- Tổng từ `11–17`: Tài.
- Tổng bằng `3` hoặc `18`: kết quả đặc biệt, cả hai cửa thua.
- Nếu `bet-settings.disable-special: true`, kết quả `3/18` được điều chỉnh để không tạo cửa đặc biệt.

Khi người chơi thắng, số tiền nhận về là tiền cược ban đầu cộng lợi nhuận sau thuế. Với thuế `0%`, payout bằng `2 × tiền cược`.

## Điểm nổi bật của fork

- Java 21 và Paper API 1.21.4+; không còn phụ thuộc NMS theo từng phiên bản.
- Scheduler native của Paper cho global, async và player entity; có đường thực thi economy thống nhất cho Paper/Folia.
- SQLite WAL thay cho mỗi phiên một file YAML.
- Transaction journal cho debit, payout và refund với các trạng thái phục hồi rõ ràng.
- Health lock tự pause game khi database/provider không còn ở trạng thái an toàn.
- Shutdown chờ economy operation đang chạy và đánh dấu `UNKNOWN` nếu không xác định được kết quả.
- Lịch sử phiên dùng cache LRU có giới hạn; SQLite vẫn là nguồn dữ liệu chính.
- Immutable API snapshots, cancellable pre-bet event và API async cho settlement/history.
- Discord webhook có bounded queue, template cache và xử lý rate limit.
- Gradle Kotlin DSL, dependency locking, JaCoCo, GitHub Actions và Dependabot.
- Hỗ trợ message pack tiếng Anh và tiếng Việt.

## Yêu cầu

| Thành phần | Yêu cầu |
|---|---|
| Java | 21 |
| Server | Paper 1.21.4+ hoặc Folia tương thích |
| Economy bridge | Vault; dùng VaultUnlocked trên Folia |
| Economy provider | Một provider tương thích với server và bridge đã chọn |

Tích hợp tùy chọn:

- PlaceholderAPI.
- PlayerPoints.
- Floodgate/Geyser cho Bedrock forms.
- Discord Webhook.

> [!CAUTION]
> `folia-supported: true` cho phép plugin được nạp trên Folia, nhưng Vault bridge và economy provider cũng phải hỗ trợ Folia. Hãy hoàn thành [runtime checklist](docs/runtime-test-checklist.md) với đúng provider của server trước khi triển khai production.

## Build

Repository đã kèm Gradle Wrapper 9.6.1, không cần cài Gradle hệ thống:

```bash
git clone https://github.com/Alexteens24/TaiXiu.git
cd TaiXiu
./gradlew clean build
```

Artifact được tạo tại:

```text
taixiu-plugin/build/libs/TaiXiu-3.0.0.jar
```

Kiểm tra riêng:

```bash
./gradlew test
./gradlew check
```

## Cài đặt

1. Cài Java 21 và Paper/Folia tương thích.
2. Cài Vault cùng economy provider; với Folia hãy dùng bridge/provider hỗ trợ Folia.
3. Chép `TaiXiu-3.0.0.jar` vào thư mục `plugins/`.
4. Khởi động server một lần để sinh cấu hình và `plugins/TaiXiu/taixiu.db`.
5. Chỉnh `plugins/TaiXiu/config.yml`, sau đó dùng `/taixiuadmin reload` hoặc restart server.
6. Test debit, payout, restart và recovery trên server tạm trước khi mở cho người chơi.

Không thay file JAR hoặc xóa `taixiu.db` khi server đang chạy.

## Phiên và dữ liệu

Phiên được lưu trong `plugins/TaiXiu/taixiu.db`:

- Database mới bắt đầu từ phiên `0`.
- Phiên kế tiếp chỉ được tạo sau khi settlement, payout và journal của phiên hiện tại hoàn tất an toàn.
- Khi restart, plugin đọc ID lớn nhất trong bảng `sessions`; locale `en/vi` không làm reset số phiên.
- Nếu shutdown xảy ra giữa payout, phiên hiện tại có thể được giữ lại và health-lock cho tới khi transaction được kiểm tra.

Các bảng chính gồm `sessions`, `bets`, `payouts` và `transaction_journal`. SQLite chạy WAL, foreign keys và schema migrations.

### Nâng cấp từ 2.x

Ở lần khởi động 3.0 đầu tiên, nếu database còn trống, plugin chỉ import phiên YAML mới nhất chưa kết thúc. Thư mục `session/` cũ được đổi tên thành `session-legacy-<timestamp>` để lưu trữ. Lịch sử YAML đã hoàn tất không được import tự động.

Trước khi nâng cấp:

1. Tắt server đúng cách.
2. Backup toàn bộ `plugins/TaiXiu/` và dữ liệu economy provider.
3. Thay JAR rồi khởi động server.
4. Kiểm tra log migration, session hiện tại và `/taixiuadmin health`.

Developer dùng API 2.x cần đọc [API 3.0 migration guide](docs/api-v3-migration.md).

### Retention và backup

`database.retention.mode` hỗ trợ:

- `ALL`: giữ toàn bộ phiên.
- `DAYS`: giữ phiên theo số ngày.
- `COUNT`: giữ số phiên mới nhất.

Phiên còn payout chưa xử lý sẽ không bị retention xóa. Với live backup, hãy dùng SQLite backup API/command; nếu copy file thủ công, hãy tắt server và backup sau khi WAL đã checkpoint.

## An toàn giao dịch

TaiXiu ghi intent vào journal trước khi gọi economy provider. Các trạng thái quan trọng:

- `PREPARED`: intent đã ghi, provider outcome chưa được xác nhận.
- `APPLIED`: provider báo thay đổi tiền thành công.
- `COMPLETED`: transaction và dữ liệu plugin đã hoàn tất.
- `COMPENSATED`: debit đã được hoàn lại.
- `FAILED`: provider từ chối rõ ràng.
- `UNKNOWN`: không thể chứng minh tiền đã thay đổi hay chưa.

Khi có `UNKNOWN` hoặc payout chưa xử lý, plugin health-lock và pause thay vì tự cộng tiền lần nữa. Economy provider không có idempotency key nên plugin không thể bảo đảm exactly-once tuyệt đối qua mọi thời điểm crash.

Kiểm tra trước khi reconcile:

```text
/taixiuadmin health
/taixiuadmin transaction list
```

Chỉ dùng `refund/retry/complete/fail` sau khi đã đối chiếu ledger hoặc balance thực tế của provider.

## Commands

### Người chơi — `taixiu.use`

| Lệnh | Mô tả |
|---|---|
| `/taixiu` | Mở menu chính |
| `/taixiu bet <tai|xiu> <amount>` | Đặt cược |
| `/taixiu cuoc <tai|xiu> <amount>` | Alias tiếng Việt của bet |
| `/taixiu info [session]` | Xem phiên hiện tại hoặc lịch sử |
| `/taixiu thongtin [session]` | Alias tiếng Việt của info |
| `/taixiu rules` hoặc `/taixiu luatchoi` | Xem luật |
| `/taixiu toggle` | Bật/tắt boss bar/thông báo |

### Quản trị — `taixiu.admin`

| Lệnh | Mô tả |
|---|---|
| `/taixiuadmin reload` | Reload cấu hình và integration có thể reload |
| `/taixiuadmin changestate` | Pause/resume khi health-lock cho phép |
| `/taixiuadmin settime <seconds>` | Đổi thời gian còn lại |
| `/taixiuadmin setcurrency <VAULT|PLAYERPOINTS>` | Đổi currency trước khi có bet |
| `/taixiuadmin setresult <d1> <d2> <d3>` | Ép kết quả phiên |
| `/taixiuadmin health` hoặc `suckhoe` | Xem health state |
| `/taixiuadmin health acknowledge` | Xác nhận và xóa health-lock |
| `/taixiuadmin suckhoe xacnhan` | Alias tiếng Việt của acknowledge |
| `/taixiuadmin transaction list [page] [status]` | Danh sách transaction cần xử lý |
| `/taixiuadmin giaodich danhsach [page] [status]` | Alias tiếng Việt của list |
| `/taixiuadmin transaction <id> <action> confirm [reason]` | Reconcile có xác nhận và audit reason |
| `/taixiuadmin giaodich <id> <hanh-dong> xacnhan [ly-do]` | Cú pháp alias tiếng Việt |

Action aliases:

| English | Tiếng Việt không dấu |
|---|---|
| `complete` | `hoantat` |
| `fail` | `thatbai` |
| `refund` | `hoantien` |
| `retry` | `thulai` |
| `confirm` / `acknowledge` | `xacnhan` |

Permission bổ sung: `taixiu.tax.bypass` bỏ qua thuế payout.

## Placeholders

| Placeholder | Giá trị |
|---|---|
| `%taixiu_phien%` | ID phiên hiện tại |
| `%taixiu_timeleft%` | Thời gian còn lại |
| `%taixiu_result_phien_<session>%` | Kết quả phiên |
| `%taixiu_resultformat_phien_<session>%` | Kết quả đã format theo locale |
| `%taixiu_taiplayers_phien_<session>%` | Danh sách người cược Tài |
| `%taixiu_xiuplayers_phien_<session>%` | Danh sách người cược Xỉu |
| `%taixiu_taiplayers_bet_phien_<session>%` | Tổng cược Tài |
| `%taixiu_xiuplayers_bet_phien_<session>%` | Tổng cược Xỉu |
| `%taixiu_totalbet_phien_<session>%` | Tổng cược hai cửa |

Dùng `current` thay cho `<session>` để đọc phiên hiện tại. Historical placeholder được load bất đồng bộ; request lạnh đầu tiên có thể trả giá trị loading cấu hình tại `placeholder-history-loading-value`.

## Cấu hình đáng chú ý

```yaml
locale: vi

database:
  file: taixiu.db
  history-cache-size: 256
  journal-retention-days: 90
  shutdown-transaction-timeout-seconds: 10
  retention:
    mode: ALL
    days: 90
    max-sessions: 10000

currency-settings:
  default: VAULT

discord-webhook-settings:
  queue-capacity: 256
  webhookURL: ""
```

Thay `database.file` cần restart. Các cấu hình retention, Discord và integration có thể reload bằng command admin.

## Kiểm thử và trạng thái hỗ trợ

CI chạy build, unit tests và JaCoCo quality gates. Test hiện bao phủ dice rules, payout calculation và SQLite migration/journal/retention.

Các lỗi thread của economy provider chỉ xuất hiện trên server thật. Trước release/merge, hãy hoàn thành [Paper/Folia runtime test checklist](docs/runtime-test-checklist.md), đặc biệt các trường hợp:

- Người chơi thoát đúng lúc debit/payout.
- Shutdown khi entity operation đang chờ.
- Provider đổi tiền rồi mới ném exception.
- SQLite không ghi được.
- Một payout thành công và payout kế tiếp thất bại.
- Restart với journal `PREPARED`, `APPLIED` và `UNKNOWN`.

## Đóng góp

1. Fork repository và tạo branch riêng.
2. Thực hiện thay đổi nhỏ, có phạm vi rõ ràng.
3. Chạy `./gradlew clean build`.
4. Với thay đổi economy/scheduler, đính kèm kết quả runtime checklist.
5. Mở draft pull request vào fork này.

Dependency và GitHub Actions được Dependabot kiểm tra hàng tuần.

## Credits

- Tác giả và dự án gốc: [Thuong Nguyen / Cortez Romeo](https://github.com/CortezRomeo).
- Fork 3.0 và maintenance: [Alexteens24](https://github.com/Alexteens24).
- [ConfigUpdater](https://github.com/tchristofferson/Config-Updater).
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc).
- [JSON-java](https://github.com/stleary/JSON-java).
- [JetBrains Annotations](https://github.com/JetBrains/java-annotations).

## License

Phân phối theo [GNU General Public License v3.0](LICENSE). Fork này giữ nguyên credit và lịch sử bản quyền của dự án gốc.
