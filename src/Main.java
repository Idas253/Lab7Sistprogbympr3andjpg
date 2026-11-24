import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class Main {

    // Путь к текстовому файлу с заданиями (ссылка и путь сохранения)
    private static final String TASK_FILE = "src/links.txt";

    public static void main(String[] args) {
        // 1. ЧТЕНИЕ ФАЙЛА С ЗАДАНИЯМИ
        List<String> lines;
        try {
            // Читаем все строки из файла (используем UTF-8, чтобы русские буквы читались нормально)
            lines = Files.readAllLines(Paths.get(TASK_FILE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Ошибка: Не удалось прочитать файл " + TASK_FILE);
            e.printStackTrace();
            return; // Завершаем программу, если файл не найден
        }

        // Проверяем, что в файле есть хотя бы 2 строки (для картинки и для аудио)
        if (lines.size() < 2) {
            System.err.println("Ошибка: В файле должно быть 2 строки (ссылка и путь для картинки, затем для mp3)");
            return;
        }

        // 2. РАЗБОР СТРОК (Формат: "https://... C:/путь/файл.jpg")
        // Разделяем строку по первому пробелу, чтобы отделить ссылку от пути
        String[] imageData = lines.get(0).split(" ", 2);
        String[] audioData = lines.get(1).split(" ", 2);

        if (imageData.length < 2 || audioData.length < 2) {
            System.err.println("Ошибка: Неверный формат строк. Пример: <ссылка> <путь>");
            return;
        }

        String imageUrl = imageData[0];
        String imagePath = imageData[1].trim();

        String audioUrl = audioData[0];
        String audioPath = audioData[1].trim();

        // 3. СКАЧИВАНИЕ В ДВА ПОТОКА (Параллельно)
        // Массив для хранения статуса загрузки: [0] - картинка, [1] - аудио
        boolean[] downloadSuccess = new boolean[2];

        // Создаем поток для скачивания картинки
        Thread imageThread = new Thread(() -> {
            downloadSuccess[0] = downloadFile(imageUrl, imagePath, "Картинка");
        });

        // Создаем поток для скачивания mp3
        Thread audioThread = new Thread(() -> {
            downloadSuccess[1] = downloadFile(audioUrl, audioPath, "MP3");
        });

        // Запускаем оба потока одновременно
        imageThread.start();
        audioThread.start();

        try {
            // Ждем, пока оба потока закончат работу
            imageThread.join();
            audioThread.join();
        } catch (InterruptedException e) {
            System.err.println("Ожидание скачивания было прервано.");
            return;
        }

        // 4. ВОСПРОИЗВЕДЕНИЕ
        // Если mp3 скачался успешно, запускаем плеер
        if (downloadSuccess[1]) {
            playMp3(audioPath);
        } else {
            System.err.println("Не удалось скачать mp3, воспроизведение отменено.");
        }
    }

    /**
     * Метод для скачивания файла по URL.
     * Возвращает true, если скачивание прошло успешно.
     */
    private static boolean downloadFile(String urlString, String savePath, String fileType) {
        System.out.println(fileType + ": Начинаю скачивание...");
        long startTime = System.currentTimeMillis(); // Засекаем время для проверки скорости

        try {
            File file = new File(savePath);

            // Создаем папку для сохранения, если её еще нет
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // Создать папки
            }

            // Открываем соединение с сервером
            URLConnection connection = new URL(urlString).openConnection();
            // Устанавливаем таймауты: 10 сек на подключение, 20 сек на чтение
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            // Прикидываемся браузером, чтобы сервер не блокировал загрузку
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            // Потоки для чтения из сети и записи на диск
            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(savePath)) {

                byte[] buffer = new byte[8192]; // Читаем по 8 КБ за раз
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println(fileType + ": Успешно скачано за " + (endTime - startTime) + " мс -> " + savePath);
            return true;

        } catch (IOException e) {
            System.err.println(fileType + ": ОШИБКА СКАЧИВАНИЯ (" + e.getMessage() + ")");
            return false;
        }
    }

    /**
     * Метод для воспроизведения MP3 файла.
     */
    private static void playMp3(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("Файл не найден: " + filePath);
            return;
        }

        System.out.println("Начинаю воспроизведение: " + filePath);
        try (FileInputStream fileInput = new FileInputStream(file);
             BufferedInputStream bufferedInput = new BufferedInputStream(fileInput)) {

            // Создаем плеер JLayer и запускаем
            Player player = new Player(bufferedInput);
            player.play(); // Метод блокирует программу, пока играет музыка
            player.close();

            System.out.println("Воспроизведение завершено.");

        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
        } catch (JavaLayerException e) {
            System.err.println("Ошибка декодирования MP3: " + e.getMessage());
        }
    }
}