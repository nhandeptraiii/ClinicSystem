Hệ thống Quản lý Phòng khám & Hỗ trợ Chẩn đoán Bệnh bằng AI (Clinic Management System)
Đồ án Tốt nghiệp / Luận văn Tốt nghiệp

Tác giả: Nguyễn Thành Nhân 
MSSV: B2105554

Mô tả: Hệ thống quản lý toàn diện quy trình vận hành phòng khám đa khoa, tích hợp mô hình AI (Machine Learning) để đưa ra gợi ý chẩn đoán bệnh dựa trên triệu chứng lâm sàng.

📖 Giới thiệu
Dự án Clinic System là một giải pháp phần mềm giúp số hóa quy trình khám chữa bệnh. Hệ thống không chỉ giúp quản lý lịch hẹn, hồ sơ bệnh án, đơn thuốc và nhân sự mà còn tích hợp một module AI giúp bác sĩ tham khảo dự đoán bệnh dựa trên các chỉ số và triệu chứng của bệnh nhân.

🚀 Tính năng chính
Hệ thống được chia thành các phân hệ rõ ràng phục vụ cho nhiều đối tượng người dùng:

1. Phân hệ Khách & Bệnh nhân (Public Portal)
  Trang chủ & Thông tin: Tra cứu thông tin phòng khám, chuyên khoa, đội ngũ bác sĩ.
  
  Đặt lịch khám trực tuyến (Booking):
  
      -Quy trình đặt hẹn theo từng bước (Wizard): Chọn chuyên khoa -> Chọn bác sĩ -> Chọn khung giờ -> Xác nhận.
  
      -Gửi yêu cầu đặt hẹn chờ duyệt.

  Hỗ trợ chẩn đoán AI (Self-Diagnosis):
  
      -Giao diện cho phép người dùng nhập triệu chứng lâm sàng.
  
      -Hệ thống AI phân tích và gợi ý nguy cơ bệnh lý để người dùng tham khảo trước khi đặt khám.

2. Phân hệ Tiếp đón & Quản trị (Admin & Staff)
  Dashboard & Thống kê (Analytics):
  
    -Biểu đồ thống kê doanh thu, số lượng bệnh nhân, lượt khám theo thời gian.
  
  Quản lý Lịch hẹn:
  
    -Tiếp nhận danh sách yêu cầu đặt hẹn từ Website.
    
    -Thao tác Duyệt (Approve) hoặc Từ chối (Reject) lịch hẹn.
  
  Quản lý Tài chính (Billing):
  
    -Tạo hóa đơn dịch vụ và hóa đơn thuốc.
    
    -Quản lý trạng thái thanh toán của bệnh nhân.
  
  Quản lý Tài nguyên Phòng khám:
  
    -Thuốc (Medications): Quản lý danh mục, số lượng, đơn giá thuốc.
    
    -Dịch vụ (Medical Services): Quản lý giá và thông tin các dịch vụ khám/xét nghiệm.
    
    -Phòng khám (Clinic Rooms): Quản lý danh sách phòng và trạng thái phòng.
    
    -Mẫu chỉ số xét nghiệm (Indicator Templates): Cấu hình các mẫu kết quả xét nghiệm (ví dụ: Công thức máu, Sinh hóa) để bác sĩ sử dụng.
  
  Quản lý Nhân sự & Phân quyền: Quản lý danh sách bác sĩ, nhân viên và phân quyền truy cập hệ thống.

3. Phân hệ Bác sĩ (Doctor)
  Quản lý Lịch làm việc (Work Schedule):
  
    -Đăng ký và xem lịch trực cá nhân.
  
  Quy trình Khám chữa bệnh (Visit Management):
  
    -Tiếp nhận bệnh nhân: Xem danh sách bệnh nhân chờ khám.
    
    -Chẩn đoán: Ghi nhận triệu chứng, chẩn đoán sơ bộ.
    
    -Chỉ định Cận lâm sàng (Service Orders): Chỉ định các xét nghiệm/dịch vụ cần thiết cho bệnh nhân.
    
    -Kết quả Cận lâm sàng: Nhập hoặc xem kết quả xét nghiệm từ các chỉ định.
  
  Kê đơn thuốc (Prescription): Lên đơn thuốc điện tử dựa trên kho thuốc hiện có

🛠️ Công nghệ sử dụng
Dự án được xây dựng theo mô hình Client-Server với kiến trúc Monolithic cho Backend, giao tiếp với Frontend thông qua chuẩn RESTful API. 
Ngoài ra, hệ thống tích hợp một AI Service độc lập để xử lý tác vụ dự đoán bệnh.

Backend
  Ngôn ngữ: Java 17

  Framework: Spring Boot 3.5.5

  Database: MySQL
  
  Security: Spring Security, JWT (OAuth2 Resource Server)
  
  Build Tool: Gradle

Frontend
  Framework: Vue.js 3 (Composition API)
  
  Ngôn ngữ: TypeScript
  
  Build Tool: Vite
  
  Styling: Tailwind CSS
  
  State Management: Pinia

AI Service
  Ngôn ngữ: Python
  
  Libraries: Scikit-learn, Pandas, Flask (hoặc tương đương để chạy API).

📂 Cấu trúc thư mục
ClinicSystem/
├── Backend/                  # Mã nguồn Java Spring Boot
│   ├── src/main/java/        # Controllers, Services, Models, Repositories
│   └── build.gradle.kts      # Cấu hình Gradle dependencies
├── Frontend/                 # Mã nguồn Vue.js
│   ├── src/
│   │   ├── views/            # Các màn hình giao diện (Login, Dashboard...)
│   │   ├── components/       # Các components tái sử dụng
│   │   └── services/         # Gọi API Backend
│   └── vite.config.ts        # Cấu hình Vite
└── ai-diagnosis-service/     # Mã nguồn Python AI
    ├── diagnosis_api.py      # API Endpoint cho AI
    └── train_diagnosis_model.py # Script huấn luyện mô hình
