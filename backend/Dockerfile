# Этап 1: Сборка (builder)
FROM golang:1.27-alpine AS builder

# Папка внутри контейнера, где будет идти работа
WORKDIR /app

# Копируем файлы зависимостей и скачиваем их
COPY go.mod go.sum ./
RUN go mod download

# Копируем весь остальной код проекта
COPY . .

# Собираем бинарник приложения. Назовем его 'coffee-api'
RUN CGO_ENABLED=0 GOOS=linux go build -o coffee-api ./cmd/api/main.go

# Этап 2: Финальный легковесный образ
FROM alpine:latest

WORKDIR /root/

# Копируем только готовый бинарник из первого этапа
COPY --from=builder /app/coffee-api .

# Сообщаем, что контейнер будет слушать 8080 порт
EXPOSE 8080

# Команда запуска
CMD ["./coffee-api"]