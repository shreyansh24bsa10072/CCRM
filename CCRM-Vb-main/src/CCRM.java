import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

enum Semester { SPRING, SUMMER, FALL }

enum Grade {
    S(10.0), A(9.0), B(8.0), C(7.0), D(6.0), E(5.0), F(0.0);

    private final double points;
    Grade(double points) { this.points = points; }
    public double getPoints() { return points; }
    public static Grade fromMarks(double marks) {
        if (marks >= 90) return S;
        if (marks >= 80) return A;
        if (marks >= 70) return B;
        if (marks >= 60) return C;
        if (marks >= 50) return D;
        if (marks >= 40) return E;
        return F;
    }
}

interface Persistable {
    String toCSV();
}

interface Searchable<T> {
    List<T> search(String query);
}

final class CourseCode {
    private final String code;
    public CourseCode(String code) {
        if (code == null || code.trim().isEmpty()) throw new IllegalArgumentException("Course code cannot be empty");
        this.code = code.trim().toUpperCase();
    }
    public String getCode() { return code; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseCode that = (CourseCode) o;
        return code.equals(that.code);
    }
    @Override public int hashCode() { return code.hashCode(); }
    @Override public String toString() { return code; }
}

abstract class Person {
    protected String id;
    protected String fullName;
    protected String email;
    protected boolean active;
    protected LocalDateTime createdDate;

    public Person(String id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.active = true;
        this.createdDate = LocalDateTime.now();
    }

    public abstract String getProfile();

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setActive(boolean active) { this.active = active; }
}

class Student extends Person implements Persistable {
    private String regNo;
    private Map<String, Enrollment> enrollments;

    public Student(String id, String regNo, String fullName, String email) {
        super(id, fullName, email);
        this.regNo = regNo;
        this.enrollments = new HashMap<>();
    }

    public void enroll(Course course, Semester semester, int year) {
        if (enrollments.containsKey(course.getCode().getCode())) {
            throw new DuplicateEnrollmentException("Student already enrolled in course: " + course.getCode());
        }
        enrollments.put(course.getCode().getCode(), new Enrollment(this, course, semester, year));
    }

    public void unenroll(String courseCode) {
        enrollments.remove(courseCode);
    }

    public void recordGrade(String courseCode, double marks) {
        Enrollment enrollment = enrollments.get(courseCode);
        if (enrollment != null) {
            enrollment.recordMarks(marks);
        }
    }

    public double calculateGPA() {
        if (enrollments.isEmpty()) return 0.0;
        double totalPoints = 0.0;
        int totalCredits = 0;
        for (Enrollment enrollment : enrollments.values()) {
            if (enrollment.getGrade() != null) {
                totalPoints += enrollment.getGrade().getPoints() * enrollment.getCourse().getCredits();
                totalCredits += enrollment.getCourse().getCredits();
            }
        }
        return totalCredits > 0 ? totalPoints / totalCredits : 0.0;
    }

    public String getTranscript() {
        StringBuilder transcript = new StringBuilder();
        transcript.append("Transcript for ").append(fullName).append(" (").append(regNo).append(")\n");
        transcript.append("GPA: ").append(String.format("%.2f", calculateGPA())).append("\n\n");
        transcript.append("Courses:\n");
        enrollments.values().forEach(enrollment -> transcript.append(enrollment).append("\n"));
        return transcript.toString();
    }

    @Override
    public String getProfile() {
        return String.format("Student ID: %s, Reg No: %s, Name: %s, Email: %s, Active: %s",
                id, regNo, fullName, email, active);
    }

    @Override
    public String toCSV() {
        return String.join(",", id, regNo, fullName, email, String.valueOf(active),
                createdDate.toString());
    }

    public String getRegNo() { return regNo; }
    public Map<String, Enrollment> getEnrollments() { return new HashMap<>(enrollments); }
}

class Instructor extends Person {
    private String department;

    public Instructor(String id, String fullName, String email, String department) {
        super(id, fullName, email);
        this.department = department;
    }

    @Override
    public String getProfile() {
        return String.format("Instructor ID: %s, Name: %s, Email: %s, Department: %s, Active: %s",
                id, fullName, email, department, active);
    }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}

class Course implements Persistable, Searchable<Course> {
    private CourseCode code;
    private String title;
    private int credits;
    private Instructor instructor;
    private Semester semester;
    private String department;
    private boolean active;

    private Course(Builder builder) {
        this.code = builder.code;
        this.title = builder.title;
        this.credits = builder.credits;
        this.instructor = builder.instructor;
        this.semester = builder.semester;
        this.department = builder.department;
        this.active = true;
    }

    public static class Builder {
        private CourseCode code;
        private String title;
        private int credits;
        private Instructor instructor;
        private Semester semester;
        private String department;

        public Builder setCode(CourseCode code) { this.code = code; return this; }
        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setCredits(int credits) { this.credits = credits; return this; }
        public Builder setInstructor(Instructor instructor) { this.instructor = instructor; return this; }
        public Builder setSemester(Semester semester) { this.semester = semester; return this; }
        public Builder setDepartment(String department) { this.department = department; return this; }

        public Course build() {
            if (code == null || title == null || credits <= 0) {
                throw new IllegalStateException("Course code, title and credits are required");
            }
            return new Course(this);
        }
    }

    @Override
    public List<Course> search(String query) {
        return Collections.singletonList(this);
    }

    @Override
    public String toCSV() {
        return String.join(",", code.getCode(), title, String.valueOf(credits),
                instructor != null ? instructor.getId() : "",
                semester.name(), department, String.valueOf(active));
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%d credits) - %s", code, title, credits, department);
    }

    public CourseCode getCode() { return code; }
    public String getTitle() { return title; }
    public int getCredits() { return credits; }
    public Instructor getInstructor() { return instructor; }
    public Semester getSemester() { return semester; }
    public String getDepartment() { return department; }
    public boolean isActive() { return active; }
    public void setTitle(String title) { this.title = title; }
    public void setCredits(int credits) { this.credits = credits; }
    public void setInstructor(Instructor instructor) { this.instructor = instructor; }
    public void setSemester(Semester semester) { this.semester = semester; }
    public void setDepartment(String department) { this.department = department; }
    public void setActive(boolean active) { this.active = active; }
}

class Enrollment {
    private Student student;
    private Course course;
    private Semester semester;
    private int year;
    private Double marks;
    private Grade grade;
    private LocalDateTime enrollmentDate;

    public Enrollment(Student student, Course course, Semester semester, int year) {
        this.student = student;
        this.course = course;
        this.semester = semester;
        this.year = year;
        this.enrollmentDate = LocalDateTime.now();
    }

    public void recordMarks(double marks) {
        this.marks = marks;
        this.grade = Grade.fromMarks(marks);
    }

    @Override
    public String toString() {
        return String.format("%s - %s: %s (%.2f) - Grade: %s",
                course.getCode(), course.getTitle(),
                marks != null ? String.format("%.2f", marks) : "N/A",
                grade != null ? grade.getPoints() : 0.0,
                grade != null ? grade.name() : "Not Graded");
    }

    public Student getStudent() { return student; }
    public Course getCourse() { return course; }
    public Semester getSemester() { return semester; }
    public int getYear() { return year; }
    public Double getMarks() { return marks; }
    public Grade getGrade() { return grade; }
    public LocalDateTime getEnrollmentDate() { return enrollmentDate; }
}

class DuplicateEnrollmentException extends RuntimeException {
    public DuplicateEnrollmentException(String message) { super(message); }
}

class MaxCreditLimitExceededException extends RuntimeException {
    public MaxCreditLimitExceededException(String message) { super(message); }
}

class AppConfig {
    private static AppConfig instance;
    private String dataDirectory;
    private int maxCreditsPerSemester;

    private AppConfig() {
        this.dataDirectory = "ccrm_data";
        this.maxCreditsPerSemester = 24;
        createDataDirectory();
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void createDataDirectory() {
        try {
            Files.createDirectories(Paths.get(dataDirectory));
        } catch (IOException e) {
            System.err.println("Failed to create data directory: " + e.getMessage());
        }
    }

    public String getDataDirectory() { return dataDirectory; }
    public int getMaxCreditsPerSemester() { return maxCreditsPerSemester; }
}

class StudentService implements Searchable<Student> {
    private Map<String, Student> students;

    public StudentService() { this.students = new HashMap<>(); }

    public void addStudent(Student student) {
        students.put(student.getId(), student);
    }

    public Student getStudent(String id) { return students.get(id); }

    public void updateStudent(String id, String fullName, String email) {
        Student student = students.get(id);
        if (student != null) {
            student.setFullName(fullName);
            student.setEmail(email);
        }
    }

    public void deactivateStudent(String id) {
        Student student = students.get(id);
        if (student != null) {
            student.setActive(false);
        }
    }

    public List<Student> listStudents() {
        return new ArrayList<>(students.values());
    }

    @Override
    public List<Student> search(String query) {
        return students.values().stream()
                .filter(s -> s.getFullName().toLowerCase().contains(query.toLowerCase()) ||
                        s.getRegNo().toLowerCase().contains(query.toLowerCase()) ||
                        s.getEmail().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }
}

class CourseService implements Searchable<Course> {
    private Map<String, Course> courses;

    public CourseService() { this.courses = new HashMap<>(); }

    public void addCourse(Course course) {
        courses.put(course.getCode().getCode(), course);
    }

    public Course getCourse(String code) { return courses.get(code); }

    public void updateCourse(String code, String title, int credits, String department) {
        Course course = courses.get(code);
        if (course != null) {
            course.setTitle(title);
            course.setCredits(credits);
            course.setDepartment(department);
        }
    }

    public void deactivateCourse(String code) {
        Course course = courses.get(code);
        if (course != null) {
            course.setActive(false);
        }
    }

    public List<Course> listCourses() {
        return new ArrayList<>(courses.values());
    }

    public List<Course> searchByInstructor(String instructorId) {
        return courses.values().stream()
                .filter(c -> c.getInstructor() != null && c.getInstructor().getId().equals(instructorId))
                .collect(Collectors.toList());
    }

    public List<Course> searchByDepartment(String department) {
        return courses.values().stream()
                .filter(c -> c.getDepartment().equalsIgnoreCase(department))
                .collect(Collectors.toList());
    }

    public List<Course> searchBySemester(Semester semester) {
        return courses.values().stream()
                .filter(c -> c.getSemester() == semester)
                .collect(Collectors.toList());
    }

    @Override
    public List<Course> search(String query) {
        return courses.values().stream()
                .filter(c -> c.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        c.getCode().getCode().toLowerCase().contains(query.toLowerCase()) ||
                        c.getDepartment().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }
}

class EnrollmentService {
    private StudentService studentService;
    private CourseService courseService;
    private List<Enrollment> enrollments;

    public EnrollmentService(StudentService studentService, CourseService courseService) {
        this.studentService = studentService;
        this.courseService = courseService;
        this.enrollments = new ArrayList<>();
    }

    public void enrollStudent(String studentId, String courseCode, Semester semester, int year) {
        Student student = studentService.getStudent(studentId);
        Course course = courseService.getCourse(courseCode);

        if (student == null || course == null) {
            throw new IllegalArgumentException("Student or course not found");
        }

        int currentCredits = getCurrentSemesterCredits(studentId, semester, year);
        if (currentCredits + course.getCredits() > AppConfig.getInstance().getMaxCreditsPerSemester()) {
            throw new MaxCreditLimitExceededException("Credit limit exceeded for semester");
        }

        student.enroll(course, semester, year);
        enrollments.add(new Enrollment(student, course, semester, year));
    }

    private int getCurrentSemesterCredits(String studentId, Semester semester, int year) {
        return enrollments.stream()
                .filter(e -> e.getStudent().getId().equals(studentId) &&
                        e.getSemester() == semester &&
                        e.getYear() == year)
                .mapToInt(e -> e.getCourse().getCredits())
                .sum();
    }

    public void recordGrade(String studentId, String courseCode, double marks) {
        Student student = studentService.getStudent(studentId);
        if (student != null) {
            student.recordGrade(courseCode, marks);
        }
    }

    public List<Enrollment> getStudentEnrollments(String studentId) {
        return enrollments.stream()
                .filter(e -> e.getStudent().getId().equals(studentId))
                .collect(Collectors.toList());
    }
}

class FileService {
    private String dataDirectory;

    public FileService() {
        this.dataDirectory = AppConfig.getInstance().getDataDirectory();
    }

    public void exportStudents(List<Student> students) throws IOException {
        Path filePath = Paths.get(dataDirectory, "students.csv");
        List<String> lines = students.stream()
                .map(Student::toCSV)
                .collect(Collectors.toList());
        Files.write(filePath, lines);
    }

    public void exportCourses(List<Course> courses) throws IOException {
        Path filePath = Paths.get(dataDirectory, "courses.csv");
        List<String> lines = courses.stream()
                .map(Course::toCSV)
                .collect(Collectors.toList());
        Files.write(filePath, lines);
    }

    public List<Student> importStudents() throws IOException {
        Path filePath = Paths.get(dataDirectory, "students.csv");
        if (!Files.exists(filePath)) return new ArrayList<>();

        return Files.lines(filePath)
                .map(line -> line.split(","))
                .filter(parts -> parts.length >= 6)
                .map(parts -> {
                    Student student = new Student(parts[0], parts[1], parts[2], parts[3]);
                    student.setActive(Boolean.parseBoolean(parts[4]));
                    return student;
                })
                .collect(Collectors.toList());
    }

    public List<Course> importCourses(Map<String, Instructor> instructors) throws IOException {
        Path filePath = Paths.get(dataDirectory, "courses.csv");
        if (!Files.exists(filePath)) return new ArrayList<>();

        return Files.lines(filePath)
                .map(line -> line.split(","))
                .filter(parts -> parts.length >= 7)
                .map(parts -> {
                    Course.Builder builder = new Course.Builder();
                    builder.setCode(new CourseCode(parts[0]))
                            .setTitle(parts[1])
                            .setCredits(Integer.parseInt(parts[2]))
                            .setSemester(Semester.valueOf(parts[4]))
                            .setDepartment(parts[5]);

                    if (!parts[3].isEmpty()) {
                        builder.setInstructor(instructors.get(parts[3]));
                    }

                    Course course = builder.build();
                    course.setActive(Boolean.parseBoolean(parts[6]));
                    return course;
                })
                .collect(Collectors.toList());
    }

    public void backupData() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupDir = Paths.get(dataDirectory, "backups", timestamp);
        Files.createDirectories(backupDir);

        Files.list(Paths.get(dataDirectory))
                .filter(path -> !path.getFileName().toString().equals("backups"))
                .forEach(path -> {
                    try {
                        Files.copy(path, backupDir.resolve(path.getFileName()));
                    } catch (IOException e) {
                        System.err.println("Failed to backup: " + path.getFileName());
                    }
                });
    }

    public long getBackupSize() throws IOException {
        Path backupsDir = Paths.get(dataDirectory, "backups");
        if (!Files.exists(backupsDir)) return 0;

        return calculateDirectorySize(backupsDir);
    }

    private long calculateDirectorySize(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return Files.size(path);
        }

        try (Stream<Path> paths = Files.list(path)) {
            return paths.mapToLong(p -> {
                try {
                    return calculateDirectorySize(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }
}

class CLI {
    private StudentService studentService;
    private CourseService courseService;
    private EnrollmentService enrollmentService;
    private FileService fileService;
    private Map<String, Instructor> instructors;
    private Scanner scanner;

    public CLI() {
        this.studentService = new StudentService();
        this.courseService = new CourseService();
        this.enrollmentService = new EnrollmentService(studentService, courseService);
        this.fileService = new FileService();
        this.instructors = new HashMap<>();
        this.scanner = new Scanner(System.in);
        loadSampleData();
    }

    private void loadSampleData() {
        Instructor instr1 = new Instructor("I001", "Dr. sumit", "sumit@uni.edu", "Computer Science");
        Instructor instr2 = new Instructor("I002", "Dr. jaiswal;", "jaiswal@uni.edu", "Mathematics");
        instructors.put(instr1.getId(), instr1);
        instructors.put(instr2.getId(), instr2);

        Course course1 = new Course.Builder()
                .setCode(new CourseCode("CS101"))
                .setTitle("Introduction to Programming")
                .setCredits(3)
                .setInstructor(instr1)
                .setSemester(Semester.FALL)
                .setDepartment("Computer Science")
                .build();

        Course course2 = new Course.Builder()
                .setCode(new CourseCode("MATH101"))
                .setTitle("Calculus I")
                .setCredits(4)
                .setInstructor(instr2)
                .setSemester(Semester.FALL)
                .setDepartment("Mathematics")
                .build();

        courseService.addCourse(course1);
        courseService.addCourse(course2);

        Student student1 = new Student("S001", "2023001", "Ankit Choudhary", "ankit.choudhary@student.edu");
        Student student2 = new Student("S002", "2023002", "Sarvagya Joshi", "sarvagya.joshi@student.edu");

        studentService.addStudent(student1);
        studentService.addStudent(student2);
    }

    public void start() {
        System.out.println("=== Campus Course & Records Manager ===");

        mainLoop: while (true) {
            printMainMenu();
            String choice = scanner.nextLine();

            switch (choice) {
                case "1": manageStudents(); break;
                case "2": manageCourses(); break;
                case "3": manageEnrollments(); break;
                case "4": manageGrades(); break;
                case "5": importExportData(); break;
                case "6": backupOperations(); break;
                case "7": generateReports(); break;
                case "8": showJavaPlatformInfo(); break;
                case "0":
                    System.out.println("Exiting CCRM. Goodbye!");
                    break mainLoop;
                default:
                    System.out.println("Invalid choice. Please try again.");
                    continue;
            }
        }
    }

    private void printMainMenu() {
        System.out.println("\n--- Main Menu ---");
        System.out.println("1. Manage Students");
        System.out.println("2. Manage Courses");
        System.out.println("3. Manage Enrollments");
        System.out.println("4. Manage Grades");
        System.out.println("5. Import/Export Data");
        System.out.println("6. Backup Operations");
        System.out.println("7. Generate Reports");
        System.out.println("8. Java Platform Info");
        System.out.println("0. Exit");
        System.out.print("Enter your choice: ");
    }

    private void manageStudents() {
        studentLoop: while (true) {
            System.out.println("\n--- Student Management ---");
            System.out.println("1. Add Student");
            System.out.println("2. List Students");
            System.out.println("3. Update Student");
            System.out.println("4. Deactivate Student");
            System.out.println("5. Search Students");
            System.out.println("6. View Student Transcript");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1": addStudent(); break;
                case "2": listStudents(); break;
                case "3": updateStudent(); break;
                case "4": deactivateStudent(); break;
                case "5": searchStudents(); break;
                case "6": viewTranscript(); break;
                case "0": break studentLoop;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    private void addStudent() {
        System.out.print("Enter Student ID: ");
        String id = scanner.nextLine();
        System.out.print("Enter Registration Number: ");
        String regNo = scanner.nextLine();
        System.out.print("Enter Full Name: ");
        String name = scanner.nextLine();
        System.out.print("Enter Email: ");
        String email = scanner.nextLine();

        Student student = new Student(id, regNo, name, email);
        studentService.addStudent(student);
        System.out.println("Student added successfully.");
    }

    private void listStudents() {
        List<Student> students = studentService.listStudents();
        if (students.isEmpty()) {
            System.out.println("No students found.");
            return;
        }
        students.forEach(s -> System.out.println(s.getProfile()));
    }

    private void updateStudent() {
        System.out.print("Enter Student ID to update: ");
        String id = scanner.nextLine();
        System.out.print("Enter new Full Name: ");
        String name = scanner.nextLine();
        System.out.print("Enter new Email: ");
        String email = scanner.nextLine();

        studentService.updateStudent(id, name, email);
        System.out.println("Student updated successfully.");
    }

    private void deactivateStudent() {
        System.out.print("Enter Student ID to deactivate: ");
        String id = scanner.nextLine();
        studentService.deactivateStudent(id);
        System.out.println("Student deactivated.");
    }

    private void searchStudents() {
        System.out.print("Enter search query: ");
        String query = scanner.nextLine();
        List<Student> results = studentService.search(query);
        if (results.isEmpty()) {
            System.out.println("No students found.");
        } else {
            results.forEach(s -> System.out.println(s.getProfile()));
        }
    }

    private void viewTranscript() {
        System.out.print("Enter Student ID: ");
        String id = scanner.nextLine();
        Student student = studentService.getStudent(id);
        if (student != null) {
            System.out.println(student.getTranscript());
        } else {
            System.out.println("Student not found.");
        }
    }

    private void manageCourses() {
        courseLoop: while (true) {
            System.out.println("\n--- Course Management ---");
            System.out.println("1. Add Course");
            System.out.println("2. List Courses");
            System.out.println("3. Update Course");
            System.out.println("4. Deactivate Course");
            System.out.println("5. Search Courses");
            System.out.println("6. Filter by Instructor");
            System.out.println("7. Filter by Department");
            System.out.println("8. Filter by Semester");
            System.out.println("0. Back to Main Menu");
            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1": addCourse(); break;
                case "2": listCourses(); break;
                case "3": updateCourse(); break;
                case "4": deactivateCourse(); break;
                case "5": searchCourses(); break;
                case "6": filterByInstructor(); break;
                case "7": filterByDepartment(); break;
                case "8": filterBySemester(); break;
                case "0": break courseLoop;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    private void addCourse() {
        System.out.print("Enter Course Code: ");
        String code = scanner.nextLine();
        System.out.print("Enter Course Title: ");
        String title = scanner.nextLine();
        System.out.print("Enter Credits: ");
        int credits = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter Department: ");
        String department = scanner.nextLine();

        Course course = new Course.Builder()
                .setCode(new CourseCode(code))
                .setTitle(title)
                .setCredits(credits)
                .setDepartment(department)
                .setSemester(Semester.FALL)
                .build();

        courseService.addCourse(course);
        System.out.println("Course added successfully.");
    }

    private void listCourses() {
        List<Course> courses = courseService.listCourses();
        if (courses.isEmpty()) {
            System.out.println("No courses found.");
            return;
        }
        courses.forEach(System.out::println);
    }

    private void updateCourse() {
        System.out.print("Enter Course Code to update: ");
        String code = scanner.nextLine();
        System.out.print("Enter new Title: ");
        String title = scanner.nextLine();
        System.out.print("Enter new Credits: ");
        int credits = Integer.parseInt(scanner.nextLine());
        System.out.print("Enter new Department: ");
        String department = scanner.nextLine();

        courseService.updateCourse(code, title, credits, department);
        System.out.println("Course updated successfully.");
    }

    private void deactivateCourse() {
        System.out.print("Enter Course Code to deactivate: ");
        String code = scanner.nextLine();
        courseService.deactivateCourse(code);
        System.out.println("Course deactivated.");
    }

    private void searchCourses() {
        System.out.print("Enter search query: ");
        String query = scanner.nextLine();
        List<Course> results = courseService.search(query);
        if (results.isEmpty()) {
            System.out.println("No courses found.");
        } else {
            results.forEach(System.out::println);
        }
    }

    private void filterByInstructor() {
        System.out.print("Enter Instructor ID: ");
        String id = scanner.nextLine();
        List<Course> results = courseService.searchByInstructor(id);
        if (results.isEmpty()) {
            System.out.println("No courses found for this instructor.");
        } else {
            results.forEach(System.out::println);
        }
    }

    private void filterByDepartment() {
        System.out.print("Enter Department: ");
        String dept = scanner.nextLine();
        List<Course> results = courseService.searchByDepartment(dept);
        if (results.isEmpty()) {
            System.out.println("No courses found in this department.");
        } else {
            results.forEach(System.out::println);
        }
    }

    private void filterBySemester() {
        System.out.println("Select Semester: 1. SPRING 2. SUMMER 3. FALL");
        String choice = scanner.nextLine();
        Semester semester = switch (choice) {
            case "1" -> Semester.SPRING;
            case "2" -> Semester.SUMMER;
            case "3" -> Semester.FALL;
            default -> null;
        };

        if (semester != null) {
            List<Course> results = courseService.searchBySemester(semester);
            if (results.isEmpty()) {
                System.out.println("No courses found for this semester.");
            } else {
                results.forEach(System.out::println);
            }
        } else {
            System.out.println("Invalid semester choice.");
        }
    }

    private void manageEnrollments() {
        System.out.print("Enter Student ID: ");
        String studentId = scanner.nextLine();
        System.out.print("Enter Course Code: ");
        String courseCode = scanner.nextLine();
        System.out.println("Select Semester: 1. SPRING 2. SUMMER 3. FALL");
        String semChoice = scanner.nextLine();
        Semester semester = switch (semChoice) {
            case "1" -> Semester.SPRING;
            case "2" -> Semester.SUMMER;
            case "3" -> Semester.FALL;
            default -> null;
        };

        if (semester != null) {
            System.out.print("Enter Year: ");
            int year = Integer.parseInt(scanner.nextLine());

            try {
                enrollmentService.enrollStudent(studentId, courseCode, semester, year);
                System.out.println("Student enrolled successfully.");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        } else {
            System.out.println("Invalid semester.");
        }
    }

    private void manageGrades() {
        System.out.print("Enter Student ID: ");
        String studentId = scanner.nextLine();
        System.out.print("Enter Course Code: ");
        String courseCode = scanner.nextLine();
        System.out.print("Enter Marks: ");
        double marks = Double.parseDouble(scanner.nextLine());

        enrollmentService.recordGrade(studentId, courseCode, marks);
        System.out.println("Grade recorded successfully.");
    }

    private void importExportData() {
        System.out.println("1. Export Data");
        System.out.println("2. Import Data");
        System.out.print("Enter choice: ");
        String choice = scanner.nextLine();

        try {
            if (choice.equals("1")) {
                fileService.exportStudents(studentService.listStudents());
                fileService.exportCourses(courseService.listCourses());
                System.out.println("Data exported successfully.");
            } else if (choice.equals("2")) {
                List<Student> students = fileService.importStudents();
                students.forEach(studentService::addStudent);
                List<Course> courses = fileService.importCourses(instructors);
                courses.forEach(courseService::addCourse);
                System.out.println("Data imported successfully.");
            }
        } catch (IOException e) {
            System.out.println("Error during file operation: " + e.getMessage());
        }
    }

    private void backupOperations() {
        try {
            fileService.backupData();
            long size = fileService.getBackupSize();
            System.out.println("Backup completed successfully.");
            System.out.println("Backup size: " + size + " bytes");
        } catch (IOException e) {
            System.out.println("Error during backup: " + e.getMessage());
        }
    }

    private void generateReports() {
        List<Student> students = studentService.listStudents();
        if (students.isEmpty()) {
            System.out.println("No students available for reports.");
            return;
        }

        Map<String, Long> gpaDistribution = students.stream()
                .collect(Collectors.groupingBy(
                        s -> {
                            double gpa = s.calculateGPA();
                            if (gpa >= 9.0) return "A (9.0+)";
                            if (gpa >= 8.0) return "B (8.0-8.9)";
                            if (gpa >= 7.0) return "C (7.0-7.9)";
                            if (gpa >= 6.0) return "D (6.0-6.9)";
                            return "F (<6.0)";
                        },
                        Collectors.counting()
                ));

        System.out.println("\n--- GPA Distribution Report ---");
        gpaDistribution.forEach((range, count) ->
                System.out.println(range + ": " + count + " students"));

        Student topStudent = students.stream()
                .max(Comparator.comparingDouble(Student::calculateGPA))
                .orElse(null);

        if (topStudent != null) {
            System.out.println("\nTop Student: " + topStudent.getFullName() +
                    " (GPA: " + String.format("%.2f", topStudent.calculateGPA()) + ")");
        }
    }

    private void showJavaPlatformInfo() {
        System.out.println("\n--- Java Platform Information ---");
        System.out.println("Java SE (Standard Edition): General purpose computing, desktop apps");
        System.out.println("Java ME (Micro Edition): Embedded systems, mobile devices");
        System.out.println("Java EE (Enterprise Edition): Large-scale distributed apps");
        System.out.println("\nThis application runs on Java SE");
        System.out.println("Java Version: " + System.getProperty("java.version"));
    }
}

public class CCRM {
    public static void main(String[] args) {
        CLI cli = new CLI();
        cli.start();
    }
}