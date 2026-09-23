# Android app

Классическое native Android-приложение на Kotlin + XML Views.

Аудио воспроизводится через AndroidX Media3/ExoPlayer. Playback вынесен в MediaSessionService, чтобы дальше поддержать фоновое воспроизведение и автомобильные сценарии.

Текущий UI — каркас первого экрана. Реальный каталог и выбор станции будут подключены после появления canonical API.
