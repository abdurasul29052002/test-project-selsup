package uz.company;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final int requestLimit;
    private final long intervalMillis;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.intervalMillis = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
            requestCounter.set(0);
            System.out.println("Request counter reset");
        }, 1, intervalMillis, timeUnit);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public synchronized void makeRequest(String json, String signature) throws InterruptedException {
        semaphore.acquire();
        if (requestCounter.incrementAndGet() > requestLimit) {
            System.out.println("Request limit exceeded");
        } else {
            try {
                Gson gson = new Gson();
                Document document = gson.fromJson(json, Document.class);
                performApiRequest(document, signature);
            } finally {
                semaphore.release();
            }
        }
    }

    private synchronized void performApiRequest(Document document, String signature) {
        File file = new File("document_" + UUID.randomUUID() + ".txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer
                    .append("Registration number: ").append(document.regNumber)
                    .append("\nProduction date: ").append(document.productionDate)
                    .append("\nDescription: ").append(document.description.participantInn)
                    .append("\nDocument type: ").append(document.docType)
                    .append("\nDocument ID: ").append(document.docId)
                    .append("\nOwner INN: ").append(document.ownerInn)
                    .append("\nProducts: ").append(document.products.toString())
                    .append("\nRegistration date: ").append(document.regDate)
                    .append("\nParticipant INN: ").append(document.participantInn)
                    .append("\nDocument status: ").append(document.docStatus)
                    .append("\nImport request: ").append(String.valueOf(document.importRequest))
                    .append("\nProduction type: ").append(document.productionType)
                    .append("\nProducer INN: ").append(document.producerInn)
                    .append("\nSignature: ").append(signature);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record Document(
            String regNumber,
            String productionDate,
            Description description,
            String docType,
            String docId,
            String ownerInn,
            List<ProductsItem> products,
            String regDate,
            String participantInn,
            String docStatus,
            boolean importRequest,
            String productionType,
            String producerInn
    ) {
    }

    private record Description(
            String participantInn
    ) {
    }

    private record ProductsItem(
            String uituCode,
            String certificateDocumentDate,
            String productionDate,
            String certificateDocumentNumber,
            String tnvedCode,
            String certificateDocument,
            String producerInn,
            String ownerInn,
            String uitCode
    ) {
    }


    public static void main(String[] args) {
        String json = """
                {
                  "description": {
                    "participantInn": "string"
                  },
                  "doc_id": "string",
                  "doc_status": "string",
                  "doc_type": "LP_INTRODUCE_GOODS",
                  "importRequest": true,
                  "owner_inn": "string",
                  "participant_inn": "string",
                  "producer_inn": "string",
                  "production_date": "2020-01-23",
                  "production_type": "string",
                  "products": [
                    {
                      "certificate_document": "string",
                      "certificate_document_date": "2020-01-23",
                      "certificate_document_number": "string",
                      "owner_inn": "string",
                      "producer_inn": "string",
                      "production_date": "2020-01-23",
                      "tnved_code": "string",
                      "uit_code": "string",
                      "uitu_code": "string"
                    }
                  ],
                  "reg_date": "2020-01-23",
                  "reg_number": "string"
                }""";
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Runnable task = () -> {
            try {
                api.makeRequest(json, "Signature");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 20; i++) {
            executorService.submit(task);
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        api.shutdown();
    }
}
