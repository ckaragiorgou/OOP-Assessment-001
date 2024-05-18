import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.InputMismatchException;

interface ErrorObserver {
    void handleError(Exception e);
}

class FileErrorLogger implements ErrorObserver {
    private String filename;

    public FileErrorLogger(String filename) {
        this.filename = filename;
    }

    @Override
    public void handleError(Exception e) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
            writer.println("Σφάλμα: " + e.getMessage());
            e.printStackTrace(writer);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}

public class AuthorizationSystem {

    private static final String url = "jdbc:mysql://localhost:3306/quizdb";
    private static final String dbUsername = "root";
    private static final String dbPassword = "";

    public static String getUsernameFromDatabase() {
        String sql = "SELECT username FROM quizdb LIMIT 1";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getString("username");
            } else {
                System.out.println("Δεν βρέθηκε όνομα χρήστη στη βάση δεδομένων.");
                return null;
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα κατά την ανάκτηση του ονόματος χρήστη από τη βάση δεδομένων: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {

        ErrorObserver errorLogger = new FileErrorLogger("error.log");

        Scanner scanner = new Scanner(System.in);
        boolean isLoggedIn = false;
        boolean keepPlaying = true;
        String username = null;


        System.out.println("Καλώς ήρθατε στο σύστημα εξουσιοδότησης!");

        while (keepPlaying) {
            isLoggedIn = false;

            while (!isLoggedIn) {
                System.out.println("1. Σύνδεση");
                System.out.println("2. Εγγραφή");
                System.out.println("3. Ιστορικό βαθμολογίας");
                System.out.println("4. Εμφάνιση χρήστη με περισσότερες συμμετοχές");
                System.out.println("5. Εμφάνιση χρήστη με κορυφαίο μέσο όρο");
                System.out.println("6. Έξοδος");

                System.out.print("Επιλέξτε μια επιλογή: ");
                int choice = scanner.nextInt();
                scanner.nextLine();

                switch (choice) {
                    case 1:
                        isLoggedIn = login(scanner);
                        if (isLoggedIn) {
                            username = getUsernameFromDatabase();
                            playAnotherQuiz(scanner, username);
                        }
                        break;
                    case 2:
                        signUp();
                        break;
                    case 3:
                        System.out.println("Εμφάνιση ιστορικού βαθμολογίας.");
                        System.out.print("Εισάγετε το όνομα χρήστη: ");
                        String viewHistoryUsername = scanner.nextLine();
                        viewHistory(viewHistoryUsername);
                        break;
                    case 4:
                        String topUser = getUserWithMostQuizParticipations();
                        if (topUser != null) {
                            System.out.println("Ο χρήστης με τις περισσότερες συμμετοχές στο κουίζ είναι: " + topUser);
                        } else {
                            System.out.println("Δεν ήταν δυνατόν να βρεθεί ο κορυφαίος χρήστης στις συμμετοχές του κουίζ.");
                        }
                        break;
                    case 5:
                        String bestUser = getUserWithBestAverageScore();
                        if (bestUser != null) {
                            double bestAverage = getAverageScore(bestUser);
                            System.out.println("Ο χρήστης με τον υψηλότερο μέσο όρο βαθμολογίας είναι: " + bestUser + " με μέσο όρο: " + bestAverage);
                        } else {
                            System.out.println("Δεν ήταν δυνατή η ανάκτηση του χρήστη με τον υψηλότερο μέσο όρο βαθμολογίας.");
                        }
                        break;
                    case 6:
                        System.out.println("Αντίο!");
                        scanner.close();
                        return;
                    default:
                        System.out.println("Παρακαλώ εισάγετε μια έγκυρη επιλογή.");
                }
            }

        }


    }

    public static boolean login(Scanner scanner) {
        System.out.print("Εισάγετε το όνομα χρήστη: ");
        String username = scanner.nextLine();
        System.out.print("Εισάγετε τον κωδικό πρόσβασης: ");
        String password = scanner.nextLine();

        if (checkCredentials(username, password)) {
            System.out.println("Επιτυχής σύνδεση! Είστε έτοιμοι να ξεκινήσετε το κουίζ και να δοκιμάσετε τις γνώσεις σας");
            startQuiz(username);
            return true;
        } else {
            System.out.println("Αποτυχία σύνδεσης. Προσπαθήστε ξανά ή δημιουργήστε ένα νέο λογαριασμό!");
            return false;
        }
    }

    public static void signUp() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Δημιουργία νέου λογαριασμού:\nΕισάγετε το όνομα χρήστη: ");
        String newUsername = scanner.nextLine();
        System.out.print("Εισάγετε τον κωδικό πρόσβασης: ");
        String newPassword = scanner.nextLine();

        if (registerUser(newUsername, newPassword)) {
            System.out.println("Επιτυχής εγγραφή! Μπορείτε τώρα να συνδεθείτε.");
        } else {
            System.out.println("Αποτυχία εγγραφής. Προσπαθήστε ξανά.");
        }
        scanner.close();
    }

    public static boolean checkCredentials(String username, String password) {
        String sql = "SELECT * FROM quizdb WHERE username = ? AND password = ?";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα στην επικοινωνία με τη βάση δεδομένων: " + e.getMessage());
            return false;
        }
    }

    public static boolean registerUser(String username, String password) {
        if (userExists(username)) {
            System.out.println("Το όνομα χρήστη υπάρχει ήδη.");
            return false;
        }

        String sql = "INSERT INTO quizdb (username, password) VALUES (?, ?)";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);
            int rowsInserted = statement.executeUpdate();
            return rowsInserted>0;
        } catch (SQLException e) {
            System.out.println("Σφάλμα στην επικοινωνία με τη βάση δεδομένων: " + e.getMessage());
            return false;
        }
    }

    public static boolean userExists(String username) {
        String sql = "SELECT * FROM quizdb WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα στην επικοινωνία με τη βάση δεδομένων: " + e.getMessage());
            return false;
        }
    }

    public static Integer getCategoryFromChoice(Integer choice) {
        switch (choice) {
            case 1:
                return 9; // Γενικές
            case 2:
                return 21; // Αθλητισμός
            case 3:
                return 22; // Γεωγραφία
            case 4:
                return 23; // Ιστορία
            case 5:
                return 25; // Τέχνες
            case 6:
                return null; // Όλες
            default:
                System.out.println("Μη έγκυρη επιλογή κατηγορίας. Επιλέξτε έναν αριθμό μεταξύ 1 και 6.");
                Scanner scanner = new Scanner(System.in);
                System.out.print("Επιλέξτε ξανά: ");
                int newChoice = scanner.nextInt();
                scanner.nextLine();
                return getCategoryFromChoice(newChoice);
        }
    }

    public static void startQuiz(String username) {
        Scanner scanner = new Scanner(System.in);
        int score = 0;

        try {
            int numQuestions = 10;

            System.out.println("Επιλέξτε την κατηγορία ερωτήσεων:");
            System.out.println("1. Γενικές");
            System.out.println("2. Αθλητισμός");
            System.out.println("3. Γεωγραφία");
            System.out.println("4. Ιστορία");
            System.out.println("5. Τέχνες");
            System.out.println("6. Όλες");
            System.out.print("Επιλέξτε μια κατηγορία (1-6): ");

            int Choice = scanner.nextInt();
            scanner.nextLine();
            int selectedCategory = getCategoryFromChoice(Choice);

            URL apiUrl = new URL("https://opentdb.com/api.php?amount=" + numQuestions + "&amp;category=" + selectedCategory);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            connection.disconnect();

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray results = jsonResponse.getJSONArray("results");

            List<Question> questions = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject obj = results.getJSONObject(i);
                String question = obj.getString("question");
                String correctAnswer = obj.getString("correct_answer");
                JSONArray incorrectAnswersJsonArray = obj.getJSONArray("incorrect_answers");
                List<String>options = new ArrayList<>();
                options.add(correctAnswer);
                for (int j = 0; j < incorrectAnswersJsonArray.length(); j++) {
                    options.add(incorrectAnswersJsonArray.getString(j));
                }
                Collections.shuffle(options);
                questions.add(new Question(question, options, correctAnswer));
            }

            for (Question q : questions) {
                System.out.println(q.question);
                for (int i = 0; i < q.options.size(); i++) {
                    System.out.println((i + 1) + ". " + q.options.get(i));
                }
                int userAnswerIndex = 0;

                boolean answeredCorrectly = false;
                while (!answeredCorrectly) {
                    System.out.print("Επιλέξτε τη σωστή απάντηση: ");

                    try {
                        userAnswerIndex = scanner.nextInt();
                        if (userAnswerIndex < 1 || userAnswerIndex > q.options.size()) {
                            throw new InputMismatchException();
                        }
                        answeredCorrectly = true;
                    } catch(InputMismatchException e) {
                        System.out.println("Μη έγκυρη απάντηση. Παρακαλώ επιλέξτε έναν αριθμό μεταξύ 1 και " + q.options.size() + ".");
                        scanner.nextLine();
                    }
                }

                String userAnswer = q.options.get(userAnswerIndex - 1);

                if (userAnswer.equals(q.correctAnswer)) {
                    System.out.println("Σωστό!");
                    score++;
                } else {
                    System.out.println("Λάθος! Η σωστή απάντηση είναι: " + q.correctAnswer);
                }
                System.out.println();
            }

            System.out.println("Τελικός βαθμός: " + score + "/" + questions.size());
        } catch (Exception e) {
            e.printStackTrace();
        }


        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword)) {
            String insertSql = "INSERT INTO user_history (username, date_time, score) VALUES (?, NOW(), ?)";
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setString(1, username);
                insertStatement.setInt(2, score);
                insertStatement.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα κατά την εισαγωγή δεδομένων στον πίνακα user_history: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void playAnotherQuiz(Scanner scanner, String username) {
        System.out.println("Θα θέλατε να παίξετε άλλο κουίζ; (Ναι/Οχι)");
        String response = scanner.nextLine().trim().toLowerCase(); // Αφαιρούμε τα περιττά κενά και μετατρέπουμε σε πεζά
        if (response.equals("ναι") || response.equals("yes")) {
            startQuiz(username);
        } else if (response.equals("οχι") || response.equals("no")) {
            System.out.println("Ευχαριστούμε για τη συμμετοχή σας!");
        } else {
            System.out.println("Παρακαλώ εισάγετε 'Ναι' ή 'Οχι'.");
        }
        System.exit(0);
    }

    public static class Question {
        public String question;
        public List<String>options;
        public String correctAnswer;

        public Question(String question, List<String> options, String correctAnswer) {
            this.question = question;
            this.options = options;
            this.correctAnswer = correctAnswer;
        }
    }

    public static void viewHistory(String username) {
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword)) {
            String selectSql = "SELECT date_time, score FROM user_history WHERE username = ?";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql)) {
                selectStatement.setString(1, username);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (!resultSet.isBeforeFirst()) {
                        System.out.println("Δεν βρέθηκε ιστορικό βαθμολογίας για τον χρήστη " + username + ".");
                    } else {
                        System.out.println("Ιστορικό βαθμολογίας για τον χρήστη " + username + ":");
                        System.out.printf("%-20s %-10s\n", "Ημερομηνία", "Βαθμολογία");

                        while (resultSet.next()) {
                            String dateTime = resultSet.getString("date_time");
                            int score = resultSet.getInt("score");
                            System.out.printf("%-20s %-10d\n", dateTime, score);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getUserWithMostQuizParticipations() {
        String sql = "SELECT username FROM user_history GROUP BY username ORDER BY COUNT(*) DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getString("username");
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα κατά την ανάκτηση του χρήστη με τις περισσότερες συμμετοχές στο κουίζ: " + e.getMessage());
        }
        return null;
    }
    public static double getAverageScore(String username) {
        String sql = "SELECT AVG(score) AS average_score FROM user_history WHERE username = ?";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("average_score");
                }
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα κατά την ανάκτηση του μέσου όρου βαθμολογίας: " + e.getMessage());
        }
        return 0;
    }
    public static String getUserWithBestAverageScore() {
        String sql = "SELECT username FROM user_history GROUP BY username ORDER BY AVG(score) DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(url, dbUsername, dbPassword);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getString("username");
            }
        } catch (SQLException e) {
            System.out.println("Σφάλμα κατά την ανάκτηση του χρήστη με τον υψηλότερο μέσο όρο βαθμολογίας: " + e.getMessage());
        }
        return null;
    }
}
