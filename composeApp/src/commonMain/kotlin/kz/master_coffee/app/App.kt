package kz.master_coffee.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import io.github.alexzhirkevich.qrose.options.*
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mastercoffee.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.outlined.Check
// === ГЛОБАЛЬНАЯ ПАМЯТЬ ЗАКАЗОВ (ДЛЯ МГНОВЕННОГО ОТОБРАЖЕНИЯ) ===
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectDragGestures
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import dev.gitlive.firebase.firestore.firestore
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive



var BgColor by mutableStateOf(Color(0xFFF8F9FA))
var CardColor by mutableStateOf(Color.White)
var TextColor by mutableStateOf(Color(0xFF1D1D1D))
var GrayColor by mutableStateOf(Color(0xFF757575))
val sessionOrders = mutableStateListOf<OrderHistoryItem>()

// === МАГИЯ ПЕРЕВОДА (СЛОВАРЬ) ===
// === ГЛОБАЛЬНЫЙ МЕНЕДЖЕР ЯЗЫКА ===
// === ГЛОБАЛЬНЫЙ МЕНЕДЖЕР ЯЗЫКА ===
var globalAppLanguage = mutableStateOf("Русский")

// === МАГИЯ ПЕРЕВОДА (СЛОВАРЬ) ===

fun String.tr(): String {
    val lang = globalAppLanguage.value
    if (lang == "Русский") return this

    val dict = mapOf(
        // === БОКОВОЕ МЕНЮ И ТЕМЫ ===
        "ЯЗЫК ПРИЛОЖЕНИЯ" to mapOf("Қазақша" to "ҚОЛДАНБА ТІЛІ", "English" to "APP LANGUAGE"),
        "Главная" to mapOf("Қазақша" to "Басты бет", "English" to "Home"),
        "Меню" to mapOf("Қазақша" to "Мәзір", "English" to "Menu"),
        "Заказы" to mapOf("Қазақша" to "Тапсырыстар", "English" to "Orders"),
        "Корзина" to mapOf("Қазақша" to "Себет", "English" to "Cart"),
        "Пригласить друзей" to mapOf("Қазақша" to "Достарды шақыру", "English" to "Invite Friends"),
        "Личные данные" to mapOf("Қазақша" to "Жеке мәліметтер", "English" to "Personal Data"),
        "О нас" to mapOf("Қазақша" to "Біз туралы", "English" to "About Us"),
        "Уведомления" to mapOf("Қазақша" to "Хабарламалар", "English" to "Notifications"),
        "Язык приложения" to mapOf("Қазақша" to "Қолданба тілі", "English" to "App Language"),
        "Интерфейс приложения" to mapOf("Қазақша" to "Қолданба интерфейсі", "English" to "App Interface"),
        "ИНТЕРФЕЙС ПРИЛОЖЕНИЯ" to mapOf("Қазақша" to "ҚОЛДАНБА ИНТЕРФЕЙСІ", "English" to "APP INTERFACE"),
        "Написать в службу поддержки" to mapOf("Қазақша" to "Қолдау қызметіне жазу", "English" to "Contact Support"),
        "Выйти из приложения" to mapOf("Қазақша" to "Қолданбадан шығу", "English" to "Log Out"),
        "УДАЛИТЬ АККАУНТ" to mapOf("Қазақша" to "АККАУНТТЫ ӨШІРУ", "English" to "DELETE ACCOUNT"),
        "Темная тема" to mapOf("Қазақша" to "Қараңғы тақырып", "English" to "Dark Theme"),
        "Светлая тема" to mapOf("Қазақша" to "Жарық тақырып", "English" to "Light Theme"),
        "Системная" to mapOf("Қазақша" to "Жүйелік", "English" to "System"),

        // === ПОДДЕРЖКА И УВЕДОМЛЕНИЯ ===
        "Опишите проблему..." to mapOf("Қазақша" to "Мәселені сипаттаңыз...", "English" to "Describe the issue..."),
        "Отправить" to mapOf("Қазақша" to "Жіберу", "English" to "Send"),
        "Включить пуши можно в настройках" to mapOf("Қазақша" to "Хабарламаларды баптауларда қосуға болады", "English" to "You can enable push notifications in settings"),
        "Включите уведомления, чтобы получать напоминания о важных событиях, акциях и статусе заказа" to mapOf("Қазақша" to "Маңызды оқиғалар, акциялар және тапсырыс күйі туралы еске салғыштар алу үшін хабарламаларды қосыңыз", "English" to "Enable notifications to get reminders about important events, promotions, and order status"),
        "Включить пуши" to mapOf("Қазақша" to "Хабарламаларды қосу", "English" to "Enable push notifications"),

        // === ПРИГЛАСИТЬ ДРУГА ===
        "Пригласи друга\nи получи 50 MC" to mapOf("Қазақша" to "Досыңды шақыр\nжәне 50 MC ал", "English" to "Invite a friend\nand get 50 MC"),
        "после первой покупки кофе другом" to mapOf("Қазақша" to "досыңыз алғаш рет кофе сатып алғаннан кейін", "English" to "after your friend's first coffee purchase"),
        "твой промокод" to mapOf("Қазақша" to "сенің промокодың", "English" to "your promo code"),
        "Копировать" to mapOf("Қазақша" to "Көшіру", "English" to "Copy"),
        "Привет! Скачивай Master Coffee и вводи мой промокод " to mapOf("Қазақша" to "Сәлем! Master Coffee жүктеп ал және тіркелу кезінде менің промокодымды енгіз ", "English" to "Hi! Download Master Coffee and enter my promo code "),
        " при регистрации! Мы оба получим по 50 MC на баланс ☕\n\nСкачать: https://mastercoffee.kz/app/invite?code=" to mapOf("Қазақша" to " ! Екеуіміз де теңгерімге 50 MC аламыз ☕\n\nЖүктеп алу: https://mastercoffee.kz/app/invite?code=", "English" to " during registration! We will both get 50 MC on our balance ☕\n\nDownload: https://mastercoffee.kz/app/invite?code="),
        "Поделиться промокодом" to mapOf("Қазақша" to "Промокодпен бөлісу", "English" to "Share promo code"),
        "Показать как это работает" to mapOf("Қазақша" to "Қалай жұмыс істейтінін көрсету", "English" to "Show how it works"),
        "Промокод скопирован!" to mapOf("Қазақша" to "Промокод көшірілді!", "English" to "Promo code copied!"),
        "Как работает система?" to mapOf("Қазақша" to "Жүйе қалай жұмыс істейді?", "English" to "How does the system work?"),
        "Вы отправляете промокод другу любым удобным способом." to mapOf("Қазақша" to "Сіз промокодты досыңызға кез келген ыңғайлы тәсілмен жібересіз.", "English" to "You send the promo code to a friend in any convenient way."),
        "Друг скачивает приложение и вводит ваш код при регистрации или в корзине." to mapOf("Қазақша" to "Досыңыз қосымшаны жүктеп алып, тіркелу кезінде немесе себетте сіздің кодыңызды енгізеді.", "English" to "Your friend downloads the app and enters your code during registration or in the cart."),
        "После того, как друг оплатит свой первый заказ, вы оба получите по 50 MC на баланс!" to mapOf("Қазақша" to "Досыңыз өзінің алғашқы тапсырысын төлегеннен кейін, екеуіңіз де теңгерімге 50 MC аласыз!", "English" to "After your friend pays for their first order, you both get 50 MC on your balance!"),
        "ПОНЯТНО" to mapOf("Қазақша" to "ТҮСІНІКТІ", "English" to "GOT IT"),

        // === О НАС ===
        "Логотип" to mapOf("Қазақша" to "Логотип", "English" to "Logo"),
        "MASTER COFFEE" to mapOf("Қазақша" to "MASTER COFFEE", "English" to "MASTER COFFEE"),
        "Казахстанская сеть specialty-кофеен, основанная в 2014 году в Астане.\n\nМы не просто варим кофе — мы развиваем кофейную культуру. У нас собственная высокотехнологичная обжарка зерен, школа бариста и десятки филиалов с уникальной атмосферой по всей стране.\n\nНаша главная миссия — делать премиальный спешелти-кофе доступным на каждый день!" to mapOf(
            "Қазақша" to "2014 жылы Астанада негізі қаланған қазақстандық specialty кофеханалар желісі.\n\nБіз жай ғана кофе қайнатпаймыз — біз кофе мәдениетін дамытамыз. Біздің өз жоғары технологиялық кофе қуыру орнымыз, бариста мектебіміз және бүкіл ел бойынша ерекше атмосферасы бар ондаған филиалдарымыз бар.\n\nБіздің басты миссиямыз — премиалды specialty кофені күнделікті қолжетімді ету!",
            "English" to "A Kazakhstani network of specialty coffee shops founded in Astana in 2014.\n\nWe don't just brew coffee — we develop coffee culture. We have our own high-tech roastery, a barista school, and dozens of branches with a unique atmosphere across the country.\n\nOur main mission is to make premium specialty coffee accessible every day!"
        ),

        // === ГЛАВНЫЙ ЭКРАН И АВТОРИЗАЦИЯ ===
        "Где заберете?" to mapOf("Қазақша" to "Қайдан аласыз?", "English" to "Where to pick up?"),
        "Забрать отсюда:" to mapOf("Қазақша" to "Осы жерден алу:", "English" to "Pick up from here:"),
        "Выбрать кофейню" to mapOf("Қазақша" to "Дәмхананы таңдау", "English" to "Select coffee shop"),
        "Баланс" to mapOf("Қазақша" to "Теңгерім", "English" to "Balance"),
        "КАРТА КОФЕМАНА" to mapOf("Қазақша" to "КОФЕМАН КАРТАСЫ", "English" to "COFFEE LOVER CARD"),
        "Ваша скидка: " to mapOf("Қазақша" to "Сіздің жеңілдігіңіз: ", "English" to "Your discount: "),
        "До скидки " to mapOf("Қазақша" to "Жеңілдікке дейін ", "English" to "Until discount "),
        "Осталось заказов: " to mapOf("Қазақша" to "Қалған тапсырыстар: ", "English" to "Orders left: "),
        "Достигнута максимальная скидка!" to mapOf("Қазақша" to "Ең жоғары жеңілдікке жеттіңіз!", "English" to "Maximum discount reached!"),
        "МОЙ QR" to mapOf("Қазақша" to "МЕНІҢ QR", "English" to "MY QR"),
        "Добро пожаловать" to mapOf("Қазақша" to "Қош келдіңіз", "English" to "Welcome"),
        "Введите номер телефона для входа" to mapOf("Қазақша" to "Кіру үшін телефон нөмірін енгізіңіз", "English" to "Enter phone number to login"),
        "Получить код" to mapOf("Қазақша" to "Кодты алу", "English" to "Get Code"),
        "Введите код" to mapOf("Қазақша" to "Кодты енгізіңіз", "English" to "Enter Code"),
        "Код отправлен на номер " to mapOf("Қазақша" to "Код мына нөмірге жіберілді ", "English" to "Code sent to number "),
        "Подтвердить" to mapOf("Қазақша" to "Растау", "English" to "Confirm"),
        "Как вас зовут?" to mapOf("Қазақша" to "Атыңыз кім?", "English" to "What's your name?"),
        "Ваше имя для профиля и заказов" to mapOf("Қазақша" to "Профиль мен тапсырыстарға арналған атыңыз", "English" to "Your name for profile and orders"),
        "Имя" to mapOf("Қазақша" to "Аты", "English" to "Name"),
        "Начать работу" to mapOf("Қазақша" to "Бастау", "English" to "Get Started"),
        "Кофеман" to mapOf("Қазақша" to "Кофеман", "English" to "Coffee Lover"),
        "ID пользователя" to mapOf("Қазақша" to "Пайдаланушы ID", "English" to "User ID"),
        "СОХРАНИТЬ" to mapOf("Қазақша" to "САҚТАУ", "English" to "SAVE"),

        // === МЕНЮ: КАТЕГОРИИ, ДОБАВКИ, НАПИТКИ ===
        "Поиск напитка..." to mapOf("Қазақша" to "Сусынды іздеу...", "English" to "Search for a drink..."),
        "Ничего не найдено" to mapOf("Қазақша" to "Ештеңе табылмады", "English" to "Nothing found"),
        "от" to mapOf("Қазақша" to "бастап", "English" to "from"),
        "Кофе" to mapOf("Қазақша" to "Кофе", "English" to "Coffee"),
        "Чаи" to mapOf("Қазақша" to "Шайлар", "English" to "Teas"),
        "Авторские" to mapOf("Қазақша" to "Авторлық", "English" to "Signature"),
        "Холодные" to mapOf("Қазақша" to "Салқын", "English" to "Cold drinks"),
        "Капучино" to mapOf("Қазақша" to "Капучино", "English" to "Cappuccino"),
        "Латте" to mapOf("Қазақша" to "Латте", "English" to "Latte"),
        "Эспрессо" to mapOf("Қазақша" to "Эспрессо", "English" to "Espresso"),
        "Американо" to mapOf("Қазақша" to "Американо", "English" to "Americano"),

        "Без сиропа" to mapOf("Қазақша" to "Сиропсыз", "English" to "No syrup"),
        "Сироп Карамель" to mapOf("Қазақша" to "Карамель сиропы", "English" to "Caramel Syrup"),
        "Сироп Соленая Карамель" to mapOf("Қазақша" to "Тұзды карамель сиропы", "English" to "Salted Caramel Syrup"),
        "Сироп Ваниль" to mapOf("Қазақша" to "Ваниль сиропы", "English" to "Vanilla Syrup"),
        "Без эссенции" to mapOf("Қазақша" to "Эссенциясыз", "English" to "No essence"),
        "Амаретто эссенция" to mapOf("Қазақша" to "Амаретто эссенциясы", "English" to "Amaretto Essence"),
        "Вишня эссенция" to mapOf("Қазақша" to "Шие эссенциясы", "English" to "Cherry Essence"),
        "Груша эссенция" to mapOf("Қазақша" to "Алмұрт эссенциясы", "English" to "Pear Essence"),
        "Без молока" to mapOf("Қазақша" to "Сүтсіз", "English" to "No milk"),
        "Миндальное молоко" to mapOf("Қазақша" to "Бадам сүті", "English" to "Almond Milk"),
        "Кокосовое молоко" to mapOf("Қазақша" to "Кокос сүті", "English" to "Coconut Milk"),
        "Фундучное молоко" to mapOf("Қазақша" to "Орман жаңғағы сүті", "English" to "Hazelnut Milk"),

        "САХАР" to mapOf("Қазақша" to "ҚАНТ", "English" to "SUGAR"),
        "Без сахара" to mapOf("Қазақша" to "Қантсыз", "English" to "No sugar"),
        "ЗАКАЗ" to mapOf("Қазақша" to "ТАПСЫРЫС", "English" to "ORDER"),
        "На вынос" to mapOf("Қазақша" to "Алып кету", "English" to "Takeaway"),
        "В зале" to mapOf("Қазақша" to "Залда", "English" to "Dine-in"),
        "СИРОПЫ" to mapOf("Қазақша" to "СИРОПТАР", "English" to "SYRUPS"),
        "Выберите минимум 1 позицию" to mapOf("Қазақша" to "Кем дегенде 1 позицияны таңдаңыз", "English" to "Select at least 1 item"),
        "ЭССЕНЦИИ БЕЗ САХАРА" to mapOf("Қазақша" to "ҚАНТСЫЗ ЭССЕНЦИЯЛАР", "English" to "SUGAR-FREE ESSENCES"),
        "Выберите до 3 дополнительных товаров" to mapOf("Қазақша" to "3 қосымша тауарға дейін таңдаңыз", "English" to "Select up to 3 addons"),
        "МОЛОКО" to mapOf("Қазақша" to "СҮТ", "English" to "MILK"),
        "Выберите до 2 дополнительных товаров" to mapOf("Қазақша" to "2 қосымша тауарға дейін таңдаңыз", "English" to "Select up to 2 addons"),
        "ТЕМПЕРАТУРА" to mapOf("Қазақша" to "ТЕМПЕРАТУРА", "English" to "TEMPERATURE"),
        "Стандарт" to mapOf("Қазақша" to "Стандартты", "English" to "Standard"),
        "Горячее" to mapOf("Қазақша" to "Ыстық", "English" to "Hot"),
        "Холоднее" to mapOf("Қазақша" to "Салқын", "English" to "Cold"),
        "В корзину за " to mapOf("Қазақша" to "Себетке ", "English" to "Add to cart for "),

        // === КОРЗИНА И ОПЛАТА ===
        "Ваш заказ" to mapOf("Қазақша" to "Сіздің тапсырысыңыз", "English" to "Your order"),
        "В корзине ничего нет" to mapOf("Қазақша" to "Себет бос", "English" to "Cart is empty"),
        "Посмотрите новинки или все меню" to mapOf("Қазақша" to "Жаңа өнімдерді немесе барлық мәзірді қараңыз", "English" to "Check out new items or full menu"),
        "МЕНЮ" to mapOf("Қазақша" to "МӘЗІР", "English" to "MENU"),
        "Ваша скидка -3%" to mapOf("Қазақша" to "Сіздің жеңілдігіңіз -3%", "English" to "Your discount -3%"),
        "Изменить" to mapOf("Қазақша" to "Өзгерту", "English" to "Change"),
        "Комментарий" to mapOf("Қазақша" to "Пікір", "English" to "Comment"),
        "Введен" to mapOf("Қазақша" to "Енгізілді", "English" to "Entered"),
        "Промокод" to mapOf("Қазақша" to "Промокод", "English" to "Promo code"),
        "СГЕНЕРИРОВАТЬ QR" to mapOf("Қазақша" to "QR ЖАСАУ", "English" to "GENERATE QR"),
        "Покажите экран кассиру" to mapOf("Қазақша" to "Экранды кассирге көрсетіңіз", "English" to "Show screen to cashier"),
        "К оплате на кассе:" to mapOf("Қазақша" to "Кассада төлеуге:", "English" to "To pay at checkout:"),
        "Скидка 3% применена" to mapOf("Қазақша" to "3% жеңілдік қолданылды", "English" to "3% discount applied"),
        "Кассир отсканировал" to mapOf("Қазақша" to "Кассир сканерледі", "English" to "Cashier scanned"),
        "Отмена" to mapOf("Қазақша" to "Бас тарту", "English" to "Cancel"),
        "КОММЕНТАРИЙ К ЗАКАЗУ" to mapOf("Қазақша" to "ТАПСЫРЫСҚА ПІКІР", "English" to "ORDER COMMENT"),
        "Например: без сахара..." to mapOf("Қазақша" to "Мысалы: қантсыз...", "English" to "E.g.: no sugar..."),
        "ВВЕДИТЕ ПРОМОКОД" to mapOf("Қазақша" to "ПРОМОКОДТЫ ЕНГІЗІҢІЗ", "English" to "ENTER PROMO CODE"),
        "Ваш промокод" to mapOf("Қазақша" to "Сіздің промокодыңыз", "English" to "Your promo code"),
        "ПРИМЕНИТЬ" to mapOf("Қазақша" to "ҚОЛДАНУ", "English" to "APPLY"),
        "История заказов" to mapOf("Қазақша" to "Тапсырыстар тарихы", "English" to "Order history"),
        "Вы еще не делали заказ" to mapOf("Қазақша" to "Сіз әлі тапсырыс бермедіңіз", "English" to "You haven't ordered yet"),
        // === СИСТЕМА ЛОЯЛЬНОСТИ ===
        "СИСТЕМА ЛОЯЛЬНОСТИ" to mapOf("Қазақша" to "БЕЙІЛДІЛІК ЖҮЙЕСІ", "English" to "LOYALTY SYSTEM"),
        "УРОВНИ СКИДОК" to mapOf("Қазақша" to "ЖЕҢІЛДІК ДЕҢГЕЙЛЕРІ", "English" to "DISCOUNT LEVELS"),
        "КАК ПОЛУЧИТЬ МАКСИМАЛЬНУЮ СКИДКУ?*" to mapOf("Қазақша" to "ЕҢ ЖОҒАРЫ ЖЕҢІЛДІКТІ ҚАЛАЙ АЛУҒА БОЛАДЫ?*", "English" to "HOW TO GET THE MAXIMUM DISCOUNT?*"),

        // === ВЫБОР КОФЕЙНИ ===
        "Выбор кофейни" to mapOf("Қазақша" to "Дәмхананы таңдау", "English" to "Select a coffee shop"),
        "Поиск" to mapOf("Қазақша" to "Іздеу", "English" to "Search"),
        "АСТАНА" to mapOf("Қазақша" to "АСТАНА", "English" to "ASTANA"),
        "Открыто" to mapOf("Қазақша" to "Ашық", "English" to "Open"),
        "Закрыто" to mapOf("Қазақша" to "Жабық", "English" to "Closed"),
        "График работы" to mapOf("Қазақша" to "Жұмыс кестесі", "English" to "Working hours"),
        "Контакты" to mapOf("Қазақша" to "Байланыстар", "English" to "Contacts"),
        "Позвонить" to mapOf("Қазақша" to "Қоңырау шалу", "English" to "Call"),
        "Заказать здесь" to mapOf("Қазақша" to "Осы жерден тапсырыс беру", "English" to "Order here"),

        "СИСТЕМА ЛОЯЛЬНОСТИ" to mapOf("Қазақша" to "БЕЙІЛДІЛІК ЖҮЙЕСІ", "English" to "LOYALTY SYSTEM"),
        "УРОВНИ СКИДОК" to mapOf("Қазақша" to "ЖЕҢІЛДІК ДЕҢГЕЙЛЕРІ", "English" to "DISCOUNT LEVELS"),
        "КАК ПОЛУЧИТЬ МАКСИМАЛЬНУЮ СКИДКУ?*" to mapOf("Қазақша" to "ЕҢ ЖОҒАРЫ ЖЕҢІЛДІКТІ ҚАЛАЙ АЛУҒА БОЛАДЫ?*", "English" to "HOW TO GET THE MAXIMUM DISCOUNT?*"),
        "Каждая отдельная покупка** кофейного или чайного напитка приближает вас к 10% скидке:\n\n• 50 и более чеков — повышение до скидки 5%;\n• 70 и более чеков — повышение до скидки 7%;\n• 100 и более чеков — максимальная скидка 10%.\n\n*Покупка для повышения уровня засчитывается только при наличии напитка в чеке.\n**Скидка действует только на напитки." to mapOf(
            "Қазақша" to "Кофе немесе шай сусынын әрбір бөлек сатып алу** сізді 10% жеңілдікке жақындатады:\n\n• 50 және одан көп чек — 5% жеңілдікке дейін көтеру;\n• 70 және одан көп чек — 7% жеңілдікке дейін көтеру;\n• 100 және одан көп чек — ең жоғары 10% жеңілдік.\n\n*Деңгейді көтеру үшін сатып алу тек чекте сусын болған жағдайда ғана есептеледі.\n**Жеңілдік тек сусындарға ғана беріледі.",
            "English" to "Each individual purchase** of a coffee or tea drink brings you closer to a 10% discount:\n\n• 50 or more receipts — upgrade to 5% discount;\n• 70 or more receipts — upgrade to 7% discount;\n• 100 or more receipts — maximum 10% discount.\n\n*Purchase for level upgrade is only counted if there is a drink in the receipt.\n**Discount only applies to drinks."
        ),

        // === МЕНЮ: КАТЕГОРИИ И НАПИТКИ (СКРИН 2) ===
        "Кофе" to mapOf("Қазақша" to "Кофе", "English" to "Coffee"),
        "Авторские" to mapOf("Қазақша" to "Авторлық", "English" to "Signature"),
        "Чаи" to mapOf("Қазақша" to "Шайлар", "English" to "Teas"),
        "Холодные" to mapOf("Қазақша" to "Салқын", "English" to "Cold drinks"),

        "Эспрессо" to mapOf("Қазақша" to "Эспрессо", "English" to "Espresso"),
        "Американо" to mapOf("Қазақша" to "Американо", "English" to "Americano"),
        "Капучино" to mapOf("Қазақша" to "Капучино", "English" to "Cappuccino"),
        "Латте" to mapOf("Қазақша" to "Латте", "English" to "Latte"),
        "Флэт\nУайт" to mapOf("Қазақша" to "Флэт\nУайт", "English" to "Flat\nWhite"),
        "Раф" to mapOf("Қазақша" to "Раф", "English" to "Raf"),
        "Hot\nCherry" to mapOf("Қазақша" to "Ыстық\nшие", "English" to "Hot\nCherry"),
        "Батч\nDaiquiri" to mapOf("Қазақша" to "Батч\nDaiquiri", "English" to "Batch\nDaiquiri"),
        "Пряный\nлатте" to mapOf("Қазақша" to "Дәмдеуіш\nлатте", "English" to "Spiced\nLatte"),
        "Раф\nHogwarts" to mapOf("Қазақша" to "Раф\nHogwarts", "English" to "Raf\nHogwarts"),
        "Глинтвейн" to mapOf("Қазақша" to "Глинтвейн", "English" to "Mulled Wine"),
        "Манго-\nимбирь" to mapOf("Қазақша" to "Манго-\nзімбір", "English" to "Mango-\nGinger"),
        "Смородина\n-Грейпфрут" to mapOf("Қазақша" to "Қарақат\n-Грейпфрут", "English" to "Currant\n-Grapefruit"),
        "Зеленый-\nлистовой" to mapOf("Қазақша" to "Жасыл\nжапырақты", "English" to "Green\nLeaf Tea"),
        "Бамбл\nКофе" to mapOf("Қазақша" to "Бамбл\nКофе", "English" to "Bumble\nCoffee"),
        "Эспрессо\nТоник" to mapOf("Қазақша" to "Эспрессо\nТоник", "English" to "Espresso\nTonic"),
        "Матча\nТоник" to mapOf("Қазақша" to "Матча\nТоник", "English" to "Matcha\nTonic"),
        "Апельсиновый Фреш" to mapOf("Қазақша" to "Апельсин Фреші", "English" to "Orange Fresh"),

        // === ВСЕ ОПИСАНИЯ НАПИТКОВ (ЧТОБЫ БЫЛО ПОЛНОСТЬЮ) ===
        "Классический насыщенный черный кофе малого объема. Пробуждает и бодрит с первого глотка." to mapOf(
            "Қазақша" to "Кішкентай көлемдегі классикалық қанық қара кофе. Бірінші жұтымнан-ақ сергітеді.",
            "English" to "Classic rich black coffee of small volume. Awakens and invigorates from the first sip."
        ),
        "Эспрессо, разбавленный горячей водой. Идеальный баланс крепости и мягкости для любителей черного кофе." to mapOf(
            "Қазақша" to "Ыстық сумен араластырылған эспрессо. Қара кофені ұнататындар үшін сергітетін және жұмсақ дәмнің тамаша теңгерімі.",
            "English" to "Espresso diluted with hot water. The perfect balance of strength and smoothness for black coffee lovers."
        ),
        "Самый популярный кофейный напиток на основе эспрессо, с добавлением теплого молока и густой, плотной молочной пены." to mapOf(
            "Қазақша" to "Эспрессо негізіндегі ең танымал кофе сусыны, жылы сүт пен қою, тығыз сүт көбігі қосылған.",
            "English" to "The most popular espresso-based coffee drink, with the addition of warm milk and thick, dense milk foam."
        ),
        "Мягкий кофейный напиток с большим количеством молока и тонким слоем пенки. Для тех, кто любит понежнее." to mapOf(
            "Қазақша" to "Сүті көп және жұқа көбік қабаты бар жұмсақ кофе сусыны. Нәзік дәмді ұнататындар үшін.",
            "English" to "A mild coffee drink with plenty of milk and a thin layer of foam. For those who like it tender."
        ),
        "Насыщенный кофейный вкус. Двойной эспрессо и тонкая молочная пенка." to mapOf(
            "Қазақша" to "Қанық кофе дәмі. Қос эспрессо және жұқа сүт көбігі.",
            "English" to "Rich coffee taste. Double espresso and thin milk foam."
        ),
        "Нежный и невероятно сливочный кофейный напиток, взбитый с ванильным сахаром и сливками." to mapOf(
            "Қазақша" to "Ванильді қант пен қаймақпен шайқалған нәзік және керемет кілегейлі кофе сусыны.",
            "English" to "A delicate and incredibly creamy coffee drink, whipped with vanilla sugar and cream."
        ),
        "Авторский согревающий напиток с насыщенным вишневым вкусом и пряными нотками." to mapOf(
            "Қазақша" to "Қанық шие дәмі мен дәмдеуіш ноталары бар авторлық жылыту сусыны.",
            "English" to "Signature warming drink with a rich cherry flavor and spicy notes."
        ),
        "Уникальный кофейный коктейль с ярким характером и цитрусовым послевкусием." to mapOf(
            "Қазақша" to "Ашық мінезі мен цитрустық дәмі бар ерекше кофе коктейлі.",
            "English" to "A unique coffee cocktail with a bright character and a citrusy aftertaste."
        ),
        "Согревающий латте с авторским миксом специй. Идеален для прохладных дней." to mapOf(
            "Қазақша" to "Дәмдеуіштердің авторлық қоспасы бар жылы латте. Салқын күндер үшін өте қолайлы.",
            "English" to "Warming latte with a signature mix of spices. Ideal for cool days."
        ),
        "Магический сливочный напиток с секретным сиропом. Вкус, который возвращает в детство." to mapOf(
            "Қазақша" to "Құпия сироп қосылған сиқырлы кілегейлі сусын. Балалық шаққа жетелейтін дәм.",
            "English" to "A magical creamy drink with a secret syrup. A taste that brings back childhood memories."
        ),
        "Безалкогольный горячий напиток на основе сока со специями, фруктами и медом." to mapOf(
            "Қазақша" to "Дәмдеуіштер, жемістер және бал қосылған шырын негізіндегі алкогольсіз ыстық сусын.",
            "English" to "A non-alcoholic hot drink based on juice with spices, fruit, and honey."
        ),
        "Бодрящий фруктовый чай с тропическим манго и согревающим имбирем." to mapOf(
            "Қазақша" to "Тропикалық манго және жылы имбирь қосылған сергітетін жеміс шайы.",
            "English" to "Invigorating fruit tea with tropical mango and warming ginger."
        ),
        "Ягодный бум с легкой цитрусовой горчинкой. Отличный заряд витаминов." to mapOf(
            "Қазақша" to "Жеңіл цитрустық ащылығы бар жидек бумы. Витаминдердің тамаша заряды.",
            "English" to "A berry boom with a light citrus bitterness. An excellent boost of vitamins."
        ),
        "Классический зеленый чай высшего сорта. Освежает и тонизирует." to mapOf(
            "Қазақша" to "Жоғары сортты классикалық жасыл шай. Сергітеді және нәрлендіреді.",
            "English" to "Classic premium green tea. Refreshes and tones."
        ),
        "Освежающий слоистый коктейль: апельсиновый фреш, карамель и шот эспрессо." to mapOf(
            "Қазақша" to "Сергітетін қабатты коктейль: апельсин фреші, карамель және эспрессо шоты.",
            "English" to "A refreshing layered cocktail: orange fresh, caramel, and a shot of espresso."
        ),
        "Холодный кофейный хит: бодрящий эспрессо в сочетании с шипучим тоником и льдом." to mapOf(
            "Қазақша" to "Салқын кофе хиты: сергітетін эспрессо, газдалған тоник және мұз қосындысы.",
            "English" to "A cold coffee hit: invigorating espresso combined with fizzy tonic and ice."
        ),
        "Японский зеленый чай матча с тоником. Яркий цвет и необычный свежий вкус." to mapOf(
            "Қазақша" to "Тоникпен жапондық жасыл матча шайы. Ашық түс және ерекше жаңа дәм.",
            "English" to "Japanese green matcha tea with tonic. Bright color and an unusual fresh taste."
        ),
        "Свежевыжатый сок из отборных апельсинов. 100% польза." to mapOf(
            "Қазақша" to "Таңдаулы апельсиндерден жаңа сығылған шырын. 100% пайдалы.",
            "English" to "Freshly squeezed juice from selected oranges. 100% healthy."
        ),

        "3%" to mapOf("Қазақша" to "3%", "English" to "3%"),
        "5%" to mapOf("Қазақша" to "5%", "English" to "5%"),
        "7%" to mapOf("Қазақша" to "7%", "English" to "7%"),
        "10%" to mapOf("Қазақша" to "10%", "English" to "10%"),
        "До скидки " to mapOf("Қазақша" to "Жеңілдікке дейін ", "English" to "To discount "),

        "МОЙ QR-КОД" to mapOf("Қазақша" to "МЕНІҢ QR-КОДЫМ", "English" to "MY QR CODE"),
        "СКИДКА: 5%" to mapOf("Қазақша" to "ЖЕҢІЛДІК: 5%", "English" to "DISCOUNT: 5%"),
        "КОФЕ НА БАЛАНСЕ: 7" to mapOf("Қазақша" to "ТЕҢГЕРІМДЕГІ КОФЕ: 7", "English" to "COFFEE ON BALANCE: 7"),
        "ЗАКРЫТЬ" to mapOf("Қазақша" to "ЖАБУ", "English" to "CLOSE"),

        "ОПЛАТА НА КАССЕ" to mapOf("Қазақша" to "КАССАДА ТӨЛЕУ", "English" to "PAY AT CHECKOUT"),
        "Покажите QR кассиру для оплаты" to mapOf("Қазақша" to "Төлеу үшін кассирге QR көрсетіңіз", "English" to "Show QR to cashier to pay"),
        "Ваш уникальный код" to mapOf("Қазақша" to "Сіздің бірегей кодыңыз", "English" to "Your unique code"),
        "Ваша скидка" to mapOf("Қазақша" to "Сіздің жеңілдігіңіз", "English" to "Your discount"),
        "Итого к оплате" to mapOf("Қазақша" to "Төленетін барлық сома", "English" to "Total to pay"),

        // === НОВЫЕ СЛОВА ДЛЯ ЛИЧНОГО QR ===
        "Покажите QR для начисления бонусов" to mapOf("Қазақша" to "Бонустар алу үшін QR көрсетіңіз", "English" to "Show QR to earn bonuses"),
        "Ваш уникальный ID" to mapOf("Қазақша" to "Сіздің бірегей ID", "English" to "Your unique ID"),
        "Кофе на балансе" to mapOf("Қазақша" to "Теңгерімдегі кофе", "English" to "Coffee on balance"),

        // === ДЛЯ ИСТОРИИ ЗАКАЗОВ ===
        "Оплачен на кассе" to mapOf("Қазақша" to "Кассада төленді", "English" to "Paid at checkout"),
        "Только что" to mapOf("Қазақша" to "Жаңа ғана", "English" to "Just now"),

        // === ДЛЯ ВЫБОРА РАЗМЕРА ===
        "ОБЪЕМ" to mapOf("Қазақша" to "ӨЛШЕМІ", "English" to "SIZE"),

        // === СЛУЖБА ПОДДЕРЖКИ ===
        "Обратная связь по приложению MasterCoffee" to mapOf("Қазақша" to "MasterCoffee қолданбасы бойынша кері байланыс", "English" to "MasterCoffee App Feedback"),
        "Технические вопросы и ошибки" to mapOf("Қазақша" to "Техникалық сұрақтар мен қателер", "English" to "Technical issues and bugs"),
        "Заказ и качество обслуживания" to mapOf("Қазақша" to "Тапсырыс және қызмет көрсету сапасы", "English" to "Order and service quality"),
        "Карта лояльности и бонусы" to mapOf("Қазақша" to "Лоялдылық картасы және бонустар", "English" to "Loyalty card and bonuses"),
        "Пожелания и предложения" to mapOf("Қазақша" to "Тілектер мен ұсыныстар", "English" to "Feedback and suggestions"),

        // === ДЛЯ ПИСЬМА В ПОДДЕРЖКУ ===
        "Здравствуйте!" to mapOf("Қазақша" to "Сәлеметсіз бе!", "English" to "Hello!"),
        "Хочу оставить отзыв о приложении." to mapOf("Қазақша" to "Мен қосымша туралы пікір қалдырғым келеді.", "English" to "I would like to leave feedback about the app."),
        "👇 Опишите проблему или предложение ниже. При необходимости прикрепите скриншот или видео:" to mapOf("Қазақша" to "Қажет болса, скриншот немесе видео тіркеңіз 👇", "English" to "👇 Describe the issue or suggestion below. Attach a screenshot or video if necessary:"),
        "Данные пользователя (Не изменять):" to mapOf("Қазақша" to "Пайдаланушы деректері(Өзгертудің қажеті жоқ):", "English" to "User Data (Do not modify):"),
        "Почта:" to mapOf("Қазақша" to "Почта:", "English" to "Email:"),
        "ID пользователя:" to mapOf("Қазақша" to "Пайдаланушының ID:", "English" to "User ID:"),

        // === ШАБЛОНЫ ДЛЯ ТЕХНИЧЕСКИХ ВОПРОСОВ ===
        "intro_tech" to mapOf("Русский" to "Я столкнулся с технической ошибкой в приложении.", "Қазақша" to "Қолданбада техникалық қате пайда болды.", "English" to "I encountered a technical error in the app."),
        "detail_tech" to mapOf("Русский" to "👇 Опишите, что именно пошло не так и на каком экране. Укажите модель вашего телефона:", "Қазақша" to "👇 Нақты не болғанын және қай экранда екенін сипаттаңыз. Телефоныңыздың моделін көрсетіңіз:", "English" to "👇 Describe what exactly went wrong and on which screen. Specify your phone model:"),

        // === ШАБЛОНЫ ДЛЯ ЗАКАЗОВ ===
        "intro_order" to mapOf("Русский" to "У меня возник вопрос по качеству обслуживания или заказа.", "Қазақша" to "Менде қызмет көрсету сапасы немесе тапсырыс бойынша сұрақ туындады.", "English" to "I have a question regarding service quality or an order."),
        "detail_order" to mapOf("Русский" to "👇 Укажите номер заказа (если есть) и адрес кофейни:", "Қазақша" to "👇 Тапсырыс нөмірін (болса) және кофейня мекенжайын көрсетіңіз:", "English" to "👇 Please specify the order number (if any) and the coffee shop address:"),

        // === ШАБЛОНЫ ДЛЯ ЛОЯЛЬНОСТИ ===
        "intro_loyalty" to mapOf("Русский" to "Вопрос по бонусной системе или карте лояльности.", "Қазақша" to "Бонустық жүйе немесе лоялдылық картасы бойынша сұрақ.", "English" to "Question about the bonus system or loyalty card."),
        "detail_loyalty" to mapOf("Русский" to "👇 Опишите проблему с бонусами или QR-кодом:", "Қазақша" to "👇 Бонустар немесе QR-код бойынша мәселені сипаттаңыз:", "English" to "👇 Describe the issue with bonuses or the QR code:"),

        // === ШАБЛОНЫ ДЛЯ ПРЕДЛОЖЕНИЙ ===
        "intro_suggest" to mapOf("Русский" to "У меня есть предложение по улучшению Master Coffee!", "Қазақша" to "Менде Master Coffee-ні жақсарту бойынша ұсыныс бар!", "English" to "I have a suggestion to improve Master Coffee!"),
        "detail_suggest" to mapOf("Русский" to "👇 Поделитесь вашей идеей, мы её обязательно изучим:", "Қазақша" to "👇 Өз идеяңызбен бөлісіңіз, біз оны міндетті түрде зерттейміз:", "English" to "👇 Share your idea, we will definitely study it:"),

        // === ВЫБОР АВАТАРА ===
        "Выберите аватар" to mapOf("Қазақша" to "Аватарды таңдаңыз", "English" to "Select Avatar"),

        "АВАТАР" to mapOf("Қазақша" to "АВАТАР", "English" to "AVATAR"),

        // === ДЛЯ СТОРИС (FULLSCREEN) ===
        "СМОТРЕТЬ КАТАЛОГ" to mapOf("Қазақша" to "МӘЗІРДІ ҚАРАУ", "English" to "VIEW CATALOG"),
    )
    return dict[this]?.get(lang) ?: this
}

val avatarMap = mapOf(
    "avatar_1" to Res.drawable.avatar_1,
    "avatar_2" to Res.drawable.avatar_2,
    "avatar_3" to Res.drawable.avatar_3,
    "avatar_4" to Res.drawable.avatar_4,
    "avatar_5" to Res.drawable.avatar_5,
    "avatar_6" to Res.drawable.avatar_6,

)
// === УМНЫЙ ПЕРЕВОДЧИК ДЛЯ ДЕТАЛЕЙ ЗАКАЗА ===
fun String.trOrderDetails(): String {
    var text = this
    val keywords = listOf(
        "САХАР", "Без сахара", "На вынос", "В зале", "Стандарт", "Горячее", "Холоднее",
        "Сироп Карамель", "Сироп Соленая Карамель", "Сироп Ваниль", "Без сиропа",
        "Без эссенции", "Амаретто эссенция", "Вишня эссенция", "Груша эссенция",
        "Без молока", "Миндальное молоко", "Кокосовое молоко", "Фундучное молоко",
        "Эспрессо", "Американо", "Капучино", "Латте", "Флэт\nУайт", "Раф",
        "Hot\nCherry", "Батч\nDaiquiri", "Пряный\nлатте", "Раф\nHogwarts",
        "Глинтвейн", "Манго-\nимбирь", "Смородина\n-Грейпфрут", "Зеленый-\nлистовой",
        "Бамбл\nКофе", "Эспрессо\nТоник", "Матча\nТоник", "Апельсиновый Фреш"
    )
    // Пробегаемся по всем известным словам и переводим их
    keywords.forEach { word ->
        text = text.replace(word, word.tr())
    }
    return text
}

val storyBanners = listOf(
    Res.drawable.story1,
    Res.drawable.story2,
    Res.drawable.story3
)

val MasterFont: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.montserrat_regular, FontWeight.Normal),
        Font(Res.font.montserrat_medium, FontWeight.Medium),
        Font(Res.font.montserrat_bold, FontWeight.Bold)
    )

@Serializable
data class FirebaseStory(
    val id: String = "",
    val url: String = "", // Ссылка на картинку
    val active: Boolean = true
)
// === МОДЕЛИ ДЛЯ НАПИТКОВ И КОРЗИНЫ ===
@Serializable
data class CoffeeSize(val volume: String, val price: Int)

@Serializable
data class CoffeeProduct(
    val id: Int,
    val name: String,
    val description: String,
    val category: String,
    @Serializable(with = DrawableResourceSerializer::class) val imageRes: DrawableResource, // <-- УКАЗАЛИ НАШ СЕРИАЛИЗАТОР
    val sizes: List<CoffeeSize>
)
@Serializable
data class CartItem(
    val product: CoffeeProduct,
    val selectedSize: CoffeeSize,
    val quantity: Int = 1,
    val sugar: Int = 0,
    val orderType: String = "На вынос",
    val temperature: String = "Стандарт",
    val finalPrice: Int = 0,
    val addonsText: String = ""
)
object DrawableResourceSerializer : KSerializer<DrawableResource> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DrawableResource", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DrawableResource) { encoder.encodeString("") }
    override fun deserialize(decoder: Decoder): DrawableResource { decoder.decodeString(); return Res.drawable.master_logo2 } // Заглушка
}
data class OrderHistoryItem(val orderNumber: String, val date: String, val status: String, val itemsDescription: String, val price: Int, val isActive: Boolean)
data class CoffeeShop(val id: Int, val address: String, val status: String, val workingHours: String, val phone: String, val rating: String, val imageRes: DrawableResource)

// === МЕНЮ С ОПИСАНИЯМИ И ОБЪЕМАМИ ===
val menuItems = listOf(
    CoffeeProduct(1, "Эспрессо", "Классический насыщенный черный кофе малого объема. Пробуждает и бодрит с первого глотка.", "Кофе", Res.drawable.espresso, listOf(CoffeeSize("30 мл", 890))),
    CoffeeProduct(2, "Американо", "Эспрессо, разбавленный горячей водой. Идеальный баланс крепости и мягкости для любителей черного кофе.", "Кофе", Res.drawable.americano, listOf(CoffeeSize("350 мл", 990), CoffeeSize("450 мл", 1190))),
    CoffeeProduct(3, "Капучино", "Самый популярный кофейный напиток на основе эспрессо, с добавлением теплого молока и густой, плотной молочной пены.", "Кофе", Res.drawable.cappuccino, listOf(CoffeeSize("350 мл", 1290), CoffeeSize("450 мл", 1440))),
    CoffeeProduct(4, "Латте", "Мягкий кофейный напиток с большим количеством молока и тонким слоем пенки. Для тех, кто любит понежнее.", "Кофе", Res.drawable.latte, listOf(CoffeeSize("350 мл", 1290), CoffeeSize("450 мл", 1440))),
    CoffeeProduct(5, "Флэт\nУайт", "Насыщенный кофейный вкус. Двойной эспрессо и тонкая молочная пенка.", "Кофе", Res.drawable.flatwhite, listOf(CoffeeSize("250 мл", 1190))),
    CoffeeProduct(6, "Раф", "Нежный и невероятно сливочный кофейный напиток, взбитый с ванильным сахаром и сливками.", "Кофе", Res.drawable.raf, listOf(CoffeeSize("350 мл", 1690))),

    CoffeeProduct(8, "Hot\nCherry", "Авторский согревающий напиток с насыщенным вишневым вкусом и пряными нотками.", "Авторские", Res.drawable.hotcherry, listOf(CoffeeSize("350 мл", 1590))),
    CoffeeProduct(9, "Батч\nDaiquiri", "Уникальный кофейный коктейль с ярким характером и цитрусовым послевкусием.", "Авторские", Res.drawable.batchdaiquiri, listOf(CoffeeSize("350 мл", 1290))),
    CoffeeProduct(10, "Пряный\nлатте", "Согревающий латте с авторским миксом специй. Идеален для прохладных дней.", "Авторские", Res.drawable.spicedlatte, listOf(CoffeeSize("350 мл", 1590))),
    CoffeeProduct(11, "Раф\nHogwarts", "Магический сливочный напиток с секретным сиропом. Вкус, который возвращает в детство.", "Авторские", Res.drawable.rafhogwarts, listOf(CoffeeSize("350 мл", 1690))),

    CoffeeProduct(12, "Глинтвейн", "Безалкогольный горячий напиток на основе сока со специями, фруктами и медом.", "Чаи", Res.drawable.glintwine, listOf(CoffeeSize("350 мл", 1090))),
    CoffeeProduct(13, "Манго-\nимбирь", "Бодрящий фруктовый чай с тропическим манго и согревающим имбирем.", "Чаи", Res.drawable.batchkunshuaq, listOf(CoffeeSize("350 мл", 990))),
    CoffeeProduct(14, "Смородина\n-Грейпфрут", "Ягодный бум с легкой цитрусовой горчинкой. Отличный заряд витаминов.", "Чаи", Res.drawable.hotcherry, listOf(CoffeeSize("350 мл", 990))),
    CoffeeProduct(15, "Зеленый-\nлистовой", "Классический зеленый чай высшего сорта. Освежает и тонизирует.", "Чаи", Res.drawable.matchalatte, listOf(CoffeeSize("400 мл", 590))),

    CoffeeProduct(16, "Бамбл\nКофе", "Освежающий слоистый коктейль: апельсиновый фреш, карамель и шот эспрессо.", "Холодные", Res.drawable.batchbrew, listOf(CoffeeSize("450 мл", 2290))),
    CoffeeProduct(17, "Эспрессо\nТоник", "Холодный кофейный хит: бодрящий эспрессо в сочетании с шипучим тоником и льдом.", "Холодные", Res.drawable.batchbrew, listOf(CoffeeSize("450 мл", 2290))),
    CoffeeProduct(18, "Матча\nТоник", "Японский зеленый чай матча с тоником. Яркий цвет и необычный свежий вкус.", "Холодные", Res.drawable.matchalatte, listOf(CoffeeSize("450 мл", 2290))),
    CoffeeProduct(19, "Апельсиновый Фреш", "Свежевыжатый сок из отборных апельсинов. 100% польза.", "Холодные", Res.drawable.batchkunshuaq, listOf(CoffeeSize("350 мл", 1990)))
)

// === СПИСОК АДРЕСОВ ===
val masterCoffeeShops = listOf(
    CoffeeShop(1, "пр. Кабанбай батыра, 60/18", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–24:00", "+7-778‒324‒36‒24", "4.6", Res.drawable.kabanbaybatyra6018),
    CoffeeShop(2, "ТРЦ MEGA Silk Way", "Открыто", "Пн—Пт 07:14–23:00 Сб—Вс 08:14–24:00", "ok@mastercoffee.kz", "5.0", Res.drawable.megasilkway),
    CoffeeShop(3, "пр. Кабанбай батыра, 56а", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–24:00", "+7‒775‒291‒46‒62", "4.8", Res.drawable.kabanbaybatyra56a),
    CoffeeShop(4, "пр. Улы Дала, 45", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–24:00", "+7‒771‒318‒95‒00", "4.8", Res.drawable.ulydala45),
    CoffeeShop(5, "пр. Туран, 52/5", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–24:00", "+7‒771‒700‒53‒83", "4.8", Res.drawable.tyran525),
    CoffeeShop(6, "пр. Мангилик Ел, 37", "Открыто", "Пн—Пт 07:14–01:00  Сб—Вс 08:14–01:00", "+7‒775‒323‒24‒19", "4.7", Res.drawable.mangelikel37),
    CoffeeShop(7, "пр. Туран, 44Б", "Открыто", "Пн—Пт 07:14–24:00  Сб—Вс 08:14–24:00", "+7‒777‒310‒54‒48", "4.7", Res.drawable.turan44b),
    CoffeeShop(8, "пр. Аль-Фараби, 11/1", "Открыто круглосуточно", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–23:00", "+7‒747‒460‒80‒34", "4.8", Res.drawable.alfarabi11),
    CoffeeShop(9, "ул. Туркестан, 8", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–23:00", "+7‒707‒746‒71‒21", "4.8", Res.drawable.turkestan8),
    CoffeeShop(10, "ул. Сыганак, 52/2", "Открыто", "Пн—Пт 07:14–24:00  Сб—Вс 08:14–24:00", "+7‒778‒290‒65‒89", "4.8", Res.drawable.syganak522),
    CoffeeShop(11, "ул. Толе би, 52", "Открыто до 00:00", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–23:00", "+7‒747‒398‒00‒32", "4.8", Res.drawable.tolebi52),
    CoffeeShop(12, "ул. Динмухамед Конаев, 35", "Открыто", "Пн—Пт 07:14–23:00  Сб—Вс 08:14–24:00", "08:14–24:00\n+7‒702‒249‒41‒13", "4.7", Res.drawable.dinmuhamedkonaev35)
)

// === ПРЕМИАЛЬНЫЙ КОМПОНЕНТ КНОПКИ В МЕНЮ ===
@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp).background(GrayColor.copy(alpha=0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = TextColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextColor, fontSize = 16.sp, fontFamily = MasterFont, fontWeight = FontWeight.Medium)
            if (subtitle != null) { Text(subtitle, color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont) }
        }
        Text("❯", color = GrayColor.copy(alpha=0.3f), fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MasterCoffeeTheme {
        val settings = remember { Settings() }

        // === УМНАЯ СИСТЕМА ТЕМ ===
        var currentTheme by remember { mutableStateOf(settings.getString("themePreference", "Системная")) }
        val systemTheme = isSystemInDarkTheme()
        var isDarkTheme = when (currentTheme) {
            "Темная тема" -> true
            "Светлая тема" -> false
            else -> systemTheme
        }

        var currentAvatarResKey by remember { mutableStateOf(settings.getStringOrNull("userAvatarResKey")) }
        var currentAvatarKey by remember { mutableStateOf(settings.getStringOrNull("userAvatarKey")) }

        var isLoggedIn by remember { mutableStateOf(settings.getBoolean("isLoggedIn", false)) }
        var showThemeSheet by remember { mutableStateOf(false) }
        var showLanguageSheet by remember { mutableStateOf(false) }
        var savedUserName by remember { mutableStateOf(settings.getString("userName", "Гость")) }
        var currentLanguage by remember { mutableStateOf(settings.getString("language", "Русский")) }
        globalAppLanguage.value = currentLanguage
        var savedEmail by remember { mutableStateOf(settings.getString("email", "agzamkairbek29@gmail.com")) }

        var showQrBottomSheet by remember { mutableStateOf(false) }
        var showLoyaltyScreen by remember { mutableStateOf(false) }
        var showNotificationsSheet by remember { mutableStateOf(false) }
        var showSupportSheet by remember { mutableStateOf(false) }

        // Переменные для сторис
        // Было: var selectedStoryRes by remember { mutableStateOf<DrawableResource?>(null) }
        // Стало:
        var showStoryViewer by remember { mutableStateOf(false) }
        var selectedStoryIndex by remember { mutableStateOf(-1) } // Храним индекс

        var showSplash by remember { mutableStateOf(true) }

        val animatedBgColor by animateColorAsState(if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA))
        val animatedCardColor by animateColorAsState(if (isDarkTheme) Color(0xFF1E1E1E) else Color.White)
        val animatedTextColor by animateColorAsState(if (isDarkTheme) Color.White else Color(0xFF1D1D1D))
        val animatedGrayColor by animateColorAsState(if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF757575))

        LaunchedEffect(animatedBgColor, animatedCardColor, animatedTextColor, animatedGrayColor) {
            BgColor = animatedBgColor; CardColor = animatedCardColor; TextColor = animatedTextColor; GrayColor = animatedGrayColor
        }

        if (showSplash) {
            SplashScreen(onTimeout = { showSplash = false })
        } else if (!isLoggedIn) {
            LoginScreen(onLoginClick = { name ->
                savedUserName = name
                isLoggedIn = true
                settings.putString("userName", name)
                settings.putBoolean("isLoggedIn", true)
            })
        } else {
            var selectedTab by remember { mutableStateOf(0) }
            val cartItems = remember { mutableStateListOf<CartItem>() }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            // === МАГИЯ 1: Идеальное синхронное кэширование без прыжков ===
            val stories = remember {
                // Читаем кэш ПРЯМО ВО ВРЕМЯ СОЗДАНИЯ переменной, до первой отрисовки экрана!
                val savedJson = settings.getStringOrNull("cached_stories")
                val initialList = try {
                    if (savedJson != null) Json.decodeFromString<List<FirebaseStory>>(savedJson) else emptyList()
                } catch (e: Exception) { emptyList() }

                // Сразу отдаем готовый список с данными
                mutableStateListOf<FirebaseStory>().apply { addAll(initialList) }
            }

            LaunchedEffect(Unit) {
                // А здесь остается только тихая фоновая проверка с Firebase
                try {
                    val fetchedStories = dev.gitlive.firebase.Firebase.firestore.collection("stories")
                        .get()
                        .documents
                        .map { it.data<FirebaseStory>() }
                        .filter { it.active }

                    // Если админ добавил новую сторис — тихо обновляем экран и кэш
                    if (fetchedStories != stories.toList()) {
                        stories.clear()
                        stories.addAll(fetchedStories)
                        settings.putString("cached_stories", Json.encodeToString(fetchedStories))
                    }
                } catch (e: Exception) {
                    println("Ошибка загрузки сторис: ${e.message}")
                }
            }

            LaunchedEffect(Unit) {
                val savedCartJson = settings.getStringOrNull("saved_cart_json")
                if (savedCartJson != null) {
                    try {
                        val decodedCart = Json.decodeFromString<List<CartItem>>(savedCartJson)
                        val restoredCart = decodedCart.map { item ->
                            val originalProduct = menuItems.find { it.id == item.product.id }
                            if (originalProduct != null) {
                                item.copy(product = item.product.copy(imageRes = originalProduct.imageRes))
                            } else item
                        }
                        cartItems.clear()
                        cartItems.addAll(restoredCart)
                    } catch (e: Exception) {
                        println("Ошибка загрузки корзины: ${e.message}")
                    }
                }
            }

            LaunchedEffect(cartItems.toList()) {
                try {
                    val jsonString = Json.encodeToString(cartItems.toList())
                    settings.putString("saved_cart_json", jsonString)
                } catch (e: Exception) {
                    println("Ошибка сохранения корзины: ${e.message}")
                }
            }

            var activeFullScreen by remember { mutableStateOf<String?>(null) }

            // =================================================================
            // === МАГИЯ ЗДЕСЬ: Обернули всё приложение в Box (Коробку) ===
            // =================================================================
            MasterBackHandler(
                enabled = showStoryViewer || activeFullScreen != null || showLoyaltyScreen ||
                        selectedTab != 0 || showQrBottomSheet || showNotificationsSheet ||
                        showThemeSheet || showLanguageSheet || showSupportSheet
            ) {
                // Иерархия закрытия: от самого верхнего слоя к нижнему
                when {
                    showStoryViewer -> showStoryViewer = false
                    showQrBottomSheet -> showQrBottomSheet = false
                    showNotificationsSheet -> showNotificationsSheet = false
                    showThemeSheet -> showThemeSheet = false
                    showLanguageSheet -> showLanguageSheet = false
                    showSupportSheet -> showSupportSheet = false
                    activeFullScreen != null -> activeFullScreen = null
                    showLoyaltyScreen -> showLoyaltyScreen = false
                    selectedTab != 0 -> selectedTab = 0
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = CardColor, modifier = Modifier.width(320.dp)) {
                            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp).verticalScroll(rememberScrollState())) {

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(64.dp).clip(CircleShape).background(MasterTeal.copy(alpha=0.1f)).border(2.dp, MasterTeal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentAvatarKey != null && avatarMap.containsKey(currentAvatarKey)) {
                                            Image(
                                                painter = painterResource(avatarMap[currentAvatarKey]!!),
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(text = savedUserName.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MasterTeal, fontFamily = MasterFont)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(savedUserName.uppercase(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
                                        val displayId = try { dev.gitlive.firebase.Firebase.auth.currentUser?.uid?.take(6)?.uppercase() ?: "184643" } catch (e:Exception) {"184643"}
                                        Text("ID $displayId", fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                ProfileMenuItem(icon = Icons.Outlined.Group, title = "Пригласить друзей".tr()) { activeFullScreen = "Пригласить друзей"; scope.launch { drawerState.close() } }
                                ProfileMenuItem(icon = Icons.Outlined.Person, title = "Личные данные".tr()) { activeFullScreen = "Личные данные"; scope.launch { drawerState.close() } }
                                ProfileMenuItem(icon = Icons.Outlined.Info, title = "О нас".tr()) { activeFullScreen = "О нас"; scope.launch { drawerState.close() } }

                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BgColor))
                                Spacer(modifier = Modifier.height(16.dp))

                                ProfileMenuItem(icon = Icons.Outlined.Notifications, title = "Уведомления".tr()) {
                                    showNotificationsSheet = true
                                    scope.launch { drawerState.close() }
                                }

                                ProfileMenuItem(icon = Icons.Outlined.Language, title = "Язык приложения".tr(), subtitle = currentLanguage) {
                                    showLanguageSheet = true
                                    scope.launch { drawerState.close() }
                                }

                                ProfileMenuItem(icon = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, title = "Интерфейс приложения".tr(), subtitle = currentTheme.tr()) {
                                    showThemeSheet = true
                                    scope.launch { drawerState.close() }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BgColor))
                                Spacer(modifier = Modifier.height(16.dp))

                                ProfileMenuItem(icon = Icons.Outlined.Email, title = "Написать в службу поддержки".tr()) {
                                    showSupportSheet = true
                                    scope.launch { drawerState.close() }
                                }

                                ProfileMenuItem(icon = Icons.Outlined.ExitToApp, title = "Выйти из приложения".tr()) {
                                    isLoggedIn = false
                                    settings.putBoolean("isLoggedIn", false)
                                }

                                Spacer(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("УДАЛИТЬ АККАУНТ".tr(), color = GrayColor.copy(alpha=0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally), fontFamily = MasterFont)
                            }
                        }
                    }
                ) {
                    Scaffold(
                        containerColor = BgColor,
                        bottomBar = {
                            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).shadow(8.dp, RoundedCornerShape(32.dp)).background(CardColor, shape = RoundedCornerShape(32.dp))) {
                                NavigationBar(containerColor = Color.Transparent, contentColor = MasterTeal, tonalElevation = 0.dp, windowInsets = WindowInsets(0.dp)) {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0; activeFullScreen = null; showLoyaltyScreen = false },
                                        icon = { Icon(Icons.Outlined.Home, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                        label = { Text("Главная".tr(), fontSize = 10.sp, fontFamily = MasterFont) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MasterTeal, selectedTextColor = MasterTeal, unselectedIconColor = GrayColor, unselectedTextColor = GrayColor, indicatorColor = MasterTeal.copy(alpha = 0.15f))
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1; activeFullScreen = null; showLoyaltyScreen = false },
                                        icon = { Icon(Icons.Outlined.LocalCafe, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                        label = { Text("Меню".tr(), fontSize = 10.sp, fontFamily = MasterFont) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MasterTeal, selectedTextColor = MasterTeal, unselectedIconColor = GrayColor, unselectedTextColor = GrayColor, indicatorColor = MasterTeal.copy(alpha = 0.15f))
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2; activeFullScreen = null; showLoyaltyScreen = false },
                                        icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                        label = { Text("Заказы".tr(), fontSize = 10.sp, fontFamily = MasterFont) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MasterTeal, selectedTextColor = MasterTeal, unselectedIconColor = GrayColor, unselectedTextColor = GrayColor, indicatorColor = MasterTeal.copy(alpha = 0.15f))
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 3,
                                        onClick = { selectedTab = 3; activeFullScreen = null; showLoyaltyScreen = false },
                                        icon = { BadgedBox(badge = { if (cartItems.isNotEmpty()) { Badge(containerColor = Color(0xFFE53935), contentColor = Color.White) { Text(cartItems.sumOf { it.quantity }.toString(), fontFamily = MasterFont) } } }) { Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(24.dp)) } },
                                        label = { Text("Корзина".tr(), fontSize = 10.sp, fontFamily = MasterFont) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MasterTeal, selectedTextColor = MasterTeal, unselectedIconColor = GrayColor, unselectedTextColor = GrayColor, indicatorColor = MasterTeal.copy(alpha = 0.15f))
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(BgColor)) {

                            if (activeFullScreen != null) {
                                ProfileSubScreen(
                                    title = activeFullScreen!!.tr(),
                                    onBackClick = { activeFullScreen = null }
                                ) {
                                    when (activeFullScreen) {
                                        "Пригласить друзей" -> { InviteFriendsContent() }
                                        "О нас" -> { AboutUsContent() }
                                        "Личные данные" -> {
                                            val displayId = try { dev.gitlive.firebase.Firebase.auth.currentUser?.uid?.take(6)?.uppercase() ?: "184643" } catch (e:Exception) {"184643"}

                                            PersonalDataContent(
                                                currentName = savedUserName,
                                                currentEmail = savedEmail,
                                                currentId = displayId,
                                                currentAvatarKey = currentAvatarKey,
                                                onSave = { newName, newEmail, newAvatarKey ->
                                                    savedUserName = newName
                                                    savedEmail = newEmail
                                                    settings.putString("userName", newName)
                                                    settings.putString("email", newEmail)

                                                    if (newAvatarKey != null) {
                                                        settings.putString("userAvatarKey", newAvatarKey)
                                                    } else {
                                                        settings.remove("userAvatarKey")
                                                    }
                                                    currentAvatarKey = newAvatarKey
                                                    activeFullScreen = null
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (showLoyaltyScreen) {
                                LoyaltyScreen(
                                    userName = savedUserName,
                                    onBackClick = { showLoyaltyScreen = false },
                                    onOpenQr = { showQrBottomSheet = true }
                                )
                            } else {
                                when (selectedTab) {
                                    0 -> HomeScreen(
                                        userName = savedUserName,
                                        stories = stories,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onOpenQr = { showQrBottomSheet = true },
                                        onOpenLoyalty = { showLoyaltyScreen = true },
                                        onStoryClick = { index ->
                                            selectedStoryIndex = index
                                            showStoryViewer = true
                                        }
                                    )
                                    1 -> MenuScreen(coffeeList = menuItems, onAddToCart = { newItem -> cartItems.add(newItem) })
                                    2 -> OrdersScreen(onNavigateToMenu = { selectedTab = 1 })
                                    3 -> CartScreen(cartItems = cartItems, onIncrease = { item -> val index = cartItems.indexOf(item); cartItems[index] = item.copy(quantity = item.quantity + 1) }, onDecrease = { item -> val index = cartItems.indexOf(item); if (item.quantity > 1) { cartItems[index] = item.copy(quantity = item.quantity - 1) } else { cartItems.removeAt(index) } }, onCheckoutSuccess = { cartItems.clear(); selectedTab = 2 }, onNavigateToMenu = { selectedTab = 1 })
                                }
                            }
                        }
                    }
                } // Конец Scaffold и ModalNavigationDrawer

                // === БОТТОМ ШИТЫ (Оставляем их внутри Box, но вне Scaffold) ===
                if (showQrBottomSheet) {
                    MyQrBottomSheet(userName = savedUserName, onDismiss = { showQrBottomSheet = false })
                }

                if (showNotificationsSheet) {
                    val settingsManager = rememberSettingsManager()
                    ModalBottomSheet(
                        onDismissRequest = { showNotificationsSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = CardColor,
                        scrimColor = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(BgColor, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Outlined.NotificationsActive, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(64.dp))
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(text = "Включить пуши можно в настройках".tr(), color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = MasterFont)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Включите уведомления, чтобы получать напоминания о важных событиях, акциях и статусе заказа".tr(), color = GrayColor, fontSize = 15.sp, fontFamily = MasterFont, lineHeight = 22.sp)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { settingsManager.openAppSettings(); showNotificationsSheet = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                            ) { Text("Включить пуши".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont) }
                        }
                    }
                }

                if (showThemeSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showThemeSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = CardColor,
                        scrimColor = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "ИНТЕРФЕЙС ПРИЛОЖЕНИЯ".tr(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
                                Box(modifier = Modifier.size(32.dp).background(BgColor, CircleShape).clickable { showThemeSheet = false }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            val themes = listOf("Светлая тема", "Темная тема", "Системная")
                            themes.forEach { theme ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { currentTheme = theme; settings.putString("themePreference", theme); showThemeSheet = false }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(theme.tr(), color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                                    if (currentTheme == theme) { Icon(Icons.Outlined.Check, contentDescription = "Выбрано", tint = TextColor, modifier = Modifier.size(24.dp)) }
                                }
                            }
                        }
                    }
                }

                if (showLanguageSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showLanguageSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = CardColor,
                        scrimColor = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "ЯЗЫК ПРИЛОЖЕНИЯ".tr(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
                                Box(modifier = Modifier.size(32.dp).background(BgColor, CircleShape).clickable { showLanguageSheet = false }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            val languages = listOf("Русский", "Қазақша", "English")
                            languages.forEach { lang ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { currentLanguage = lang; settings.putString("language", lang); showLanguageSheet = false }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(lang, color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                                    if (currentLanguage == lang) { Icon(Icons.Outlined.Check, contentDescription = "Выбрано", tint = TextColor, modifier = Modifier.size(24.dp)) }
                                }
                            }
                        }
                    }
                }

                if (showSupportSheet) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val displayId = try { dev.gitlive.firebase.Firebase.auth.currentUser?.uid?.take(6)?.uppercase() ?: "184643" } catch (e:Exception) {"184643"}

                    ModalBottomSheet(
                        onDismissRequest = { showSupportSheet = false },
                        containerColor = CardColor,
                        scrimColor = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                            Text("Обратная связь по приложению MasterCoffee".tr(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
                            Spacer(modifier = Modifier.height(24.dp))

                            val sendEmail = { subjectKey: String, introKey: String, detailKey: String ->
                                val emailAddress = "support@mastercoffee.kz"
                                val body = """
                                    ${"Здравствуйте!".tr()}
                                    
                                    ${introKey.tr()}
                                    ${detailKey.tr()}
                                    
                                    -------------------
                                    ${"Данные пользователя (Не изменять):".tr()}
                                    ${"Почта:".tr()} $savedEmail
                                    ${"ID пользователя:".tr()} $displayId
                                """.trimIndent()

                                val encodedSubject = subjectKey.tr().replace(" ", "%20")
                                val encodedBody = body.replace(" ", "%20").replace("\n", "%0A")
                                val mailtoLink = "mailto:$emailAddress?subject=$encodedSubject&body=$encodedBody"
                                try { uriHandler.openUri(mailtoLink) } catch (e: Exception) { println("Ошибка почты") }
                                showSupportSheet = false
                            }

                            Row(modifier = Modifier.fillMaxWidth().clickable { sendEmail("Технические вопросы и ошибки", "intro_tech", "detail_tech") }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Build, null, tint = TextColor, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text("Технические вопросы и ошибки".tr(), color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                            }
                            Row(modifier = Modifier.fillMaxWidth().clickable { sendEmail("Заказ и качество обслуживания", "intro_order", "detail_order") }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Storefront, null, tint = TextColor, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text("Заказ и качество обслуживания".tr(), color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                            }
                            Row(modifier = Modifier.fillMaxWidth().clickable { sendEmail("Карта лояльности и бонусы", "intro_loyalty", "detail_loyalty") }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CardMembership, null, tint = TextColor, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text("Карта лояльности и бонусы".tr(), color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                            }
                            Row(modifier = Modifier.fillMaxWidth().clickable { sendEmail("Пожелания и предложения", "intro_suggest", "detail_suggest") }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TextColor, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Text("Пожелания и предложения".tr(), color = TextColor, fontSize = 16.sp, fontFamily = MasterFont)
                            }
                        }
                    }
                }

                // =====================================================================
                // === НАЛОЖЕНИЕ FULLSCREEN СТОРИС (САМЫЙ ВЕРХНИЙ СЛОЙ В КОРОБКЕ) ===
                // =====================================================================
                if (showStoryViewer && selectedStoryIndex >= 0) {
                    StoryViewerScreen(
                        stories = stories,
                        initialIndex = selectedStoryIndex,
                        onDismiss = {
                            showStoryViewer = false
                            selectedStoryIndex = -1
                        }
                    )
                }

            } // === КОНЕЦ BOX ===
        }
    }
}

// === ЭКРАНЫ ===
@Composable
fun LoginScreen(onLoginClick: (String) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(BgColor).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Image(painter = painterResource(Res.drawable.master_logo1), contentDescription = "Логотип MASTER COFFEE", modifier = Modifier.size(120.dp))

        Spacer(modifier = Modifier.height(16.dp))

        if (step == 1) {
            Text(text = "Добро пожаловать".tr(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextColor, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Введите номер телефона для входа".tr(), fontSize = 15.sp, color = GrayColor.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) phoneNumber = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PhoneVisualTransformation(),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MasterTeal, unfocusedBorderColor = GrayColor.copy(alpha = 0.2f), focusedContainerColor = CardColor, unfocusedContainerColor = CardColor),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontFamily = MasterFont, letterSpacing = 1.sp, color = TextColor),
                singleLine = true,
                placeholder = { Text("777 123 45 67", color = GrayColor.copy(alpha = 0.3f), fontFamily = MasterFont) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { step = 2 }, enabled = phoneNumber.length == 10,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal, disabledContainerColor = MasterTeal.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Получить код".tr(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }

        } else if (step == 2) {
            Text("Введите код".tr(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextColor, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(4.dp))
            // Хитрый момент: отделяем текст от номера телефона, чтобы переводился только текст
            Text("Код отправлен на номер ".tr() + "+7 ${phoneNumber.take(3)}***${phoneNumber.takeLast(2)}", fontSize = 15.sp, color = GrayColor.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(40.dp))

            BasicTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) otpCode = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        repeat(4) { index ->
                            val char = if (index >= otpCode.length) "" else otpCode[index].toString()
                            val isFocused = index == otpCode.length
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f).background(CardColor, RoundedCornerShape(16.dp)).border(width = if (isFocused) 2.dp else 1.dp, color = if (isFocused) MasterTeal else GrayColor.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(text = char, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont) }
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { step = 3 }, enabled = otpCode.length == 4,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Подтвердить".tr(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }

        } else {
            Text("Как вас зовут?".tr(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextColor, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ваше имя для профиля и заказов".tr(), fontSize = 15.sp, color = GrayColor.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = userName, onValueChange = { userName = it },
                placeholder = { Text("Имя".tr(), color = GrayColor.copy(alpha = 0.3f), fontFamily = MasterFont) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MasterTeal, unfocusedBorderColor = GrayColor.copy(alpha = 0.2f), focusedContainerColor = CardColor, unfocusedContainerColor = CardColor),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontFamily = MasterFont, color = TextColor),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 1. ПЕРЕВОДИМ ДО КНОПКИ:
            val defaultName = "Кофеман".tr()

            Button(
                // 2. ПЕРЕДАЕМ ГОТОВУЮ ПЕРЕМЕННУЮ:
                onClick = { onLoginClick(if (userName.isNotBlank()) userName else defaultName) },
                enabled = userName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Начать работу".tr(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
        }
    }
}

@Composable
fun HomeScreen(
    userName: String,
    stories: List<FirebaseStory>, // <--- НОВЫЙ ПАРАМЕТР: Принимаем список сторис с сервера!
    onOpenDrawer: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenLoyalty: () -> Unit,
    onStoryClick: (Int) -> Unit
) {
    val settings = remember { Settings() }
    var showAdminPanel by remember { mutableStateOf(false) }
    var orderCount by remember { mutableStateOf(settings.getInt("cached_order_count", 0)) }
    var showShopsDialog by remember { mutableStateOf(false) }
    var selectedShop by remember { mutableStateOf<CoffeeShop?>(null) }




    LaunchedEffect(Unit) {
        try {
            // Скачиваем свежий баланс в фоне и незаметно обновляем
            val freshCount = Firebase.firestore.collection("orders").get().documents.count { it.get<Boolean>("isActive") == false }
            if (orderCount != freshCount) {
                orderCount = freshCount
                settings.putInt("cached_order_count", freshCount) // Сохраняем на будущее
            }
        } catch (e: Exception) { println(e.message) }
    }

    val currentDiscount: Int
    val nextDiscount: Int
    val ordersNeeded: Int
    val progressFraction: Float

    when {
        orderCount < 50 -> { currentDiscount = 3; nextDiscount = 5; ordersNeeded = 50 - orderCount; progressFraction = orderCount.toFloat() / 50f }
        orderCount < 70 -> { currentDiscount = 5; nextDiscount = 7; ordersNeeded = 70 - orderCount; progressFraction = (orderCount - 50).toFloat() / 20f }
        orderCount < 100 -> { currentDiscount = 7; nextDiscount = 10; ordersNeeded = 100 - orderCount; progressFraction = (orderCount - 70).toFloat() / 30f }
        else -> { currentDiscount = 10; nextDiscount = 10; ordersNeeded = 0; progressFraction = 1f }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Outlined.Menu, contentDescription = "Меню".tr(), tint = TextColor, modifier = Modifier.size(32.dp).clickable { onOpenDrawer() })
            Spacer(modifier = Modifier.weight(1f))
            Image(painter = painterResource(Res.drawable.master_logo), contentDescription = "Логотип".tr(), modifier = Modifier.height(120.dp).pointerInput(Unit){detectTapGestures(
                onLongPress = {
                    // Юзер зажал палец -> Показываем админку!
                    showAdminPanel = true
                }
            )}, contentScale = ContentScale.Fit)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth().shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.08f), ambientColor = Color.Black.copy(alpha = 0.05f)).background(CardColor, shape = RoundedCornerShape(16.dp)).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).clickable { showShopsDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(20.dp)) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(if (selectedShop != null) "Забрать отсюда:".tr() else "Где заберете?".tr(), color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont)
                    Text(text = selectedShop?.address ?: "Выбрать кофейню".tr(), color = TextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = MasterFont, maxLines = 1)
                }
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(GrayColor.copy(alpha = 0.2f)))
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Баланс".tr(), color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont)
                Spacer(modifier = Modifier.height(2.dp))
                Text("${orderCount * 10} MC", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === ОБНОВЛЕННАЯ КАРУСЕЛЬ СТОРИС (С ИНТЕРНЕТА) ===
        // Проверяем, есть ли сторисы вообще, чтобы не рисовать пустое место
        if (stories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // МАГИЯ: Берем сторисы из нового списка, пришедшего с сервера
                itemsIndexed(stories) { index, story ->
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(width = 2.dp, color = MasterTeal, shape = RoundedCornerShape(20.dp))
                            .clickable {
                                onStoryClick(index) // Передаем номер сторис
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // === А ВОТ И НАШ COIL ===
                        // Он сам скачает картинку по URL и закэширует её
                        coil3.compose.AsyncImage(
                            model = story.url, // Ссылка на картинку из Firebase
                            contentDescription = "Акция",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // === ДАЛЬШЕ ИДЕТ ТВОЯ КАРТА ЛОЯЛЬНОСТИ ===
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).clickable { onOpenLoyalty() }) {
            Image(painter = painterResource(Res.drawable.card_bg), contentDescription = "Фон".tr(), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("КАРТА КОФЕМАНА".tr(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = MasterFont, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Ваша скидка: ".tr() + "$currentDiscount%", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = MasterFont)
                    }
                    Text("$currentDiscount%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp, fontFamily = MasterFont)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (ordersNeeded > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("До скидки ".tr() + "$nextDiscount%", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = MasterFont)
                            Text("Осталось заказов: ".tr() + "$ordersNeeded", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = MasterFont)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Text("Достигнута максимальная скидка!".tr(), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = MasterFont) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = maxOf(0.02f, progressFraction)).fillMaxHeight().background(Color.White, CircleShape))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(userName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, fontFamily = MasterFont, modifier = Modifier.weight(1f))
                    Button(onClick = { onOpenQr() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("МОЙ QR".tr(), color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MasterFont)
                    }
                }
            }
        }
    }

    if (showShopsDialog) { ShopSelectionDialog(onDismiss = { showShopsDialog = false }, onShopSelect = { shop -> selectedShop = shop; showShopsDialog = false }) }

    if (showAdminPanel) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAdminPanel = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Панель Администратора",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Вызываем наш компонент загрузки (который ты добавил в App.kt)
                    AdminStoryUploader(
                        onUploadComplete = {
                            // Когда фотка успешно загружена — закрываем окно
                            showAdminPanel = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuScreen(coffeeList: List<CoffeeProduct>, onAddToCart: (CartItem) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var productToCustomize by remember { mutableStateOf<CoffeeProduct?>(null) }

    val filteredCoffeeList = remember(searchQuery, coffeeList) { if (searchQuery.isBlank()) coffeeList else coffeeList.filter { it.name.contains(searchQuery, ignoreCase = true) } }
    val groupedProducts = remember(filteredCoffeeList) { filteredCoffeeList.groupBy { it.category } }
    val categories = groupedProducts.keys.toList()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }

    val categoryIndices = remember(groupedProducts) {
        val indices = mutableMapOf<String, Int>()
        var currentIndex = 0
        groupedProducts.forEach { (categoryName, products) -> indices[categoryName] = currentIndex; currentIndex += 1; currentIndex += products.size }
        indices
    }

    val firstVisibleIndex = gridState.firstVisibleItemIndex
    LaunchedEffect(firstVisibleIndex) {
        val activeCategory = categoryIndices.entries.lastOrNull { it.value <= firstVisibleIndex }?.key
        if (activeCategory != null && activeCategory != selectedCategory) { selectedCategory = activeCategory }
    }

    if (productToCustomize != null) {
        ProductOrderDialog(
            product = productToCustomize!!,
            onDismiss = { productToCustomize = null },
            // ВНИМАНИЕ: Теперь мы принимаем `size` из диалога!
            onAddToCart = { size, sugar, type, temp, finalPrice, addonsText ->
                val newItem = CartItem(
                    product = productToCustomize!!,
                    selectedSize = size, // <-- Записываем выбранный размер
                    quantity = 1,
                    sugar = sugar,
                    orderType = type,
                    temperature = temp,
                    finalPrice = finalPrice,
                    addonsText = addonsText
                )
                onAddToCart(newItem)
                productToCustomize = null
            }
        )
    } else {
        // ... твой остальной код MenuScreen
        Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
            Column(modifier = Modifier.fillMaxWidth().background(CardColor).padding(top = 16.dp, bottom = 8.dp)) {

                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск напитка...".tr(), color = GrayColor, fontFamily = MasterFont) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GrayColor) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = GrayColor.copy(alpha=0.2f), focusedBorderColor = MasterTeal, unfocusedContainerColor = BgColor, focusedContainerColor = CardColor, focusedTextColor = TextColor, unfocusedTextColor = TextColor),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MasterFont), singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                if (categories.isNotEmpty()) {
                    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            val isSelected = category == selectedCategory
                            Box(modifier = Modifier.background(if (isSelected) MasterTeal else Color.Transparent, RoundedCornerShape(20.dp)).clickable { selectedCategory = category; val targetIndex = categoryIndices[category]; if (targetIndex != null) { coroutineScope.launch { gridState.animateScrollToItem(targetIndex) } } }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(text = category.tr(), color = if (isSelected) Color.White else TextColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, fontFamily = MasterFont)
                            }
                        }
                    }
                }
            }

            if (filteredCoffeeList.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.LocalCafe, contentDescription = null, tint = GrayColor.copy(alpha=0.3f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ничего не найдено".tr(), color = GrayColor, fontSize = 16.sp, fontFamily = MasterFont)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    groupedProducts.forEach { (categoryName, products) ->
                        item(span = { GridItemSpan(maxLineSpan) }) { Text(text = categoryName.tr(), color = GrayColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = MasterFont, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp, start = 4.dp)) }
                        items(products) { product -> ProductCard(product = product) { productToCustomize = product } }
                    }
                }
            }
        }
    }
}
@Composable
fun ProductCard(product: CoffeeProduct, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(26.dp), spotColor = Color.Black.copy(alpha = 0.08f), ambientColor = Color.Black.copy(alpha = 0.08f))
            .background(CardColor, shape = RoundedCornerShape(26.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp), contentAlignment = Alignment.Center) { Image(painter = painterResource(product.imageRes), contentDescription = product.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }

            // 1. Добавили перевод для названия напитка
            Text(text = product.name.tr(), color = TextColor, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, fontFamily = MasterFont, modifier = Modifier.fillMaxWidth().height(36.dp))
            Spacer(modifier = Modifier.height(4.dp))

            val minPrice = product.sizes.minOf { it.price }
            // 2. Отделили слово "от", чтобы переводить только его
            val priceText = if (product.sizes.size > 1) "${"от".tr()} $minPrice" else "$minPrice"

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$priceText ₸", color = TextColor, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, fontFamily = MasterFont, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(22.dp).background(MasterTeal.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = "+", color = MasterTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont, modifier = Modifier.offset(y = (-1).dp))
                }
            }
        }
    }
}

@Composable
fun CartScreen(cartItems: List<CartItem>, onIncrease: (CartItem) -> Unit, onDecrease: (CartItem) -> Unit, onCheckoutSuccess: () -> Unit, onNavigateToMenu: () -> Unit) {
    val scope = rememberCoroutineScope()

    var showCommentDialog by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var showPromoDialog by remember { mutableStateOf(false) }
    var promoText by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var currentOrderNumber by remember { mutableStateOf("") }

    val subtotal = cartItems.sumOf { item ->
        val actualPrice = if (item.finalPrice > 0) item.finalPrice else item.selectedSize.price
        actualPrice * item.quantity
    }
    val totalToPay = (subtotal * 0.97).roundToInt() // Математика теперь сойдется везде
    val discount = subtotal - totalToPay

    Column(modifier = Modifier.fillMaxSize().background(BgColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Ваш заказ".tr(), color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = MasterFont) }
        Spacer(modifier = Modifier.height(16.dp))

        if (cartItems.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = MasterTeal.copy(alpha=0.6f), modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("В корзине ничего нет".tr(), color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Посмотрите новинки или все меню".tr(), color = GrayColor, fontSize = 14.sp, fontFamily = MasterFont)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onNavigateToMenu, modifier = Modifier.fillMaxWidth(0.6f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = CardColor), shape = RoundedCornerShape(16.dp)) { Text("МЕНЮ".tr(), color = TextColor, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.weight(1f)) { items(cartItems) { item -> CartItemCard(item = item, onIncrease = onIncrease, onDecrease = onDecrease) } }

                Spacer(modifier = Modifier.height(16.dp))
                // Если вдруг Divider подчеркивается красным, замени на HorizontalDivider()
                Divider(color = GrayColor.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ваша скидка -3%".tr(), color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                    Text("$discount ₸", color = GrayColor, fontSize = 16.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, fontFamily = MasterFont)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = { showCommentDialog = true }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor), border = androidx.compose.foundation.BorderStroke(1.dp, GrayColor.copy(alpha = 0.2f)), contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(if (commentText.isNotEmpty()) "Изменить".tr() else "Комментарий".tr(), fontFamily = MasterFont, fontSize = 13.sp, maxLines = 1) }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { showPromoDialog = true }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor), border = androidx.compose.foundation.BorderStroke(1.dp, GrayColor.copy(alpha = 0.2f)), contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text(if (promoText.isNotEmpty()) "Введен".tr() else "Промокод".tr(), fontFamily = MasterFont, fontSize = 13.sp, maxLines = 1) }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { currentOrderNumber = "#MC-${(1000..9999).random()}"; showQrDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MasterTeal), shape = RoundedCornerShape(16.dp)
                ) { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("СГЕНЕРИРОВАТЬ QR".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont); Text("$totalToPay ₸", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = MasterFont) } }
            }
        }
    }

    // === НАША ПРЕМИАЛЬНАЯ ШТОРКА ===
    if (showQrDialog) {
        CartQrBottomSheet(
            finalPrice = totalToPay,
            onDismiss = { showQrDialog = false },
            onSuccess = {
                // 1. Формируем подробное описание заказа
                val detailedDescription = cartItems.joinToString(separator = "\n\n") { item ->
                    val sugarText = if (item.sugar > 0) "САХАР: ${item.sugar}" else "Без сахара"
                    val addonsDisplay = if (item.addonsText.isNotEmpty()) "$sugarText, ${item.addonsText}" else sugarText
                    "• ${item.product.name} (${item.selectedSize.volume}) x${item.quantity}\n  └ $addonsDisplay, ${item.orderType}, ${item.temperature}"
                }

                // === МАГИЯ ЗДЕСЬ: Получаем реальное время устройства! ===
                val currentTime = getCurrentDateTime()

                // 2. Создаем объект заказа локально
                val newOrder = OrderHistoryItem(
                    orderNumber = currentOrderNumber,
                    date = currentTime, // <--- ТЕПЕРЬ ТУТ НАСТОЯЩЕЕ ВРЕМЯ (например: 01.04.2026 15:14)
                    status = "Оплачен на кассе",
                    itemsDescription = detailedDescription,
                    price = totalToPay,
                    isActive = true
                )

                // 3. МГНОВЕННО добавляем на самый верх списка
                sessionOrders.add(0, newOrder)

                // 4. Сразу закрываем шторку и переходим в "Заказы"
                showQrDialog = false
                onCheckoutSuccess()

                // 5. Отправляем копию в Firebase в фоне (используем GlobalScope чтобы не прервалось)
                kotlinx.coroutines.GlobalScope.launch {
                    try {
                        val orderData = mapOf(
                            "orderNumber" to newOrder.orderNumber,
                            "date" to newOrder.date, // В базу уйдет красивое настоящее время!
                            "status" to newOrder.status,
                            "itemsDescription" to newOrder.itemsDescription,
                            "price" to newOrder.price,
                            "comment" to commentText,
                            "promoCode" to promoText,
                            "isActive" to newOrder.isActive
                        )
                        dev.gitlive.firebase.Firebase.firestore.collection("orders").add(orderData)
                    } catch (e: Exception) {
                        println("Ошибка Firebase: ${e.message}")
                    }
                }
            }
        )
    }

    if (showCommentDialog) { MasterBottomSheetDialog(title = "КОММЕНТАРИЙ К ЗАКАЗУ".tr(), placeholder = "Например: без сахара...".tr(), initialText = commentText, onDismiss = { showCommentDialog = false }, onApply = { commentText = it; showCommentDialog = false }) }
    if (showPromoDialog) { MasterBottomSheetDialog(title = "ВВЕДИТЕ ПРОМОКОД".tr(), placeholder = "Ваш промокод".tr(), initialText = promoText, onDismiss = { showPromoDialog = false }, onApply = { promoText = it; showPromoDialog = false }) }
}

// === САМ КОМПОНЕНТ ШТОРКИ ОПЛАТЫ (ЛЕЖИТ ПРЯМО ТУТ ЖЕ) ===

@Composable
fun CartItemCard(item: CartItem, onIncrease: (CartItem) -> Unit, onDecrease: (CartItem) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
        Image(painter = painterResource(item.product.imageRes), contentDescription = null, modifier = Modifier.size(90.dp))
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Переводим название напитка
            Text(text = item.product.name.tr(), color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Opacity, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = item.selectedSize.volume, color = MasterTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Детали (сахар, сиропы и т.д.)
            val sugarText = if (item.sugar > 0) "САХАР: ${item.sugar}" else "Без сахара"
            val addonsDisplay = if (item.addonsText.isNotEmpty()) "$sugarText, ${item.addonsText}" else sugarText

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.LocalCafe, contentDescription = null, tint = GrayColor.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                // Используем trOrderDetails(), чтобы детали тоже переводились на лету
                Text(text = addonsDisplay.trOrderDetails(), color = GrayColor.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = MasterFont)
            }
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.ShoppingBag, contentDescription = null, tint = GrayColor.copy(alpha=0.6f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                // Динамически переводим тип заказа и температуру
                Text(text = "${item.orderType.tr()} • ${item.temperature.tr()}", color = GrayColor.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = MasterFont)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // 1. Считаем базовую цену за одну позицию
                    val basePrice = if (item.finalPrice > 0) item.finalPrice else item.selectedSize.price

                    // 2. Считаем цену со скидкой 3% с ПРАВИЛЬНЫМ округлением (спасенный тенге!)
                    val discountedPrice = (basePrice * 0.97).roundToInt()

                    // Новая цена (например, 1213 ₸)
                    Text(
                        text = "$discountedPrice ₸",
                        color = MasterTeal,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = MasterFont
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Старая зачеркнутая цена (например, 1250 ₸)
                    Text(
                        text = "$basePrice ₸",
                        color = GrayColor.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        modifier = Modifier.padding(bottom = 2.dp),
                        fontFamily = MasterFont
                    )
                }

                // Кнопки управления количеством
                Row(modifier = Modifier.height(34.dp).background(MasterTeal.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onDecrease(item) }, contentAlignment = Alignment.Center) {
                        if (item.quantity == 1) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MasterTeal, modifier = Modifier.size(18.dp))
                        } else {
                            Text("-", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = MasterFont, modifier = Modifier.offset(y = (-1).dp))
                        }
                    }
                    Text("${item.quantity}", color = MasterTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp), fontFamily = MasterFont)
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onIncrease(item) }, contentAlignment = Alignment.Center) {
                        Text("+", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = MasterFont, modifier = Modifier.offset(y = (-1).dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OrdersScreen(onNavigateToMenu: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            // Подтягиваем старые заказы из Firebase
            val fetched = dev.gitlive.firebase.Firebase.firestore.collection("orders").get().documents.map { doc ->
                OrderHistoryItem(
                    orderNumber = doc.get("orderNumber"),
                    date = doc.get("date"),
                    status = doc.get("status"),
                    itemsDescription = doc.get("itemsDescription"),
                    price = doc.get<Int>("price"),
                    isActive = doc.get("isActive")
                )
            }.reversed()

            // Добавляем их к нам, если их еще нет в списке (исключаем дубликаты)
            fetched.forEach { fetchedItem ->
                if (sessionOrders.none { it.orderNumber == fetchedItem.orderNumber }) {
                    sessionOrders.add(fetchedItem)
                }
            }
        } catch (e: Exception) {
            println("Не удалось загрузить старые заказы: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("История заказов".tr(), color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading && sessionOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MasterTeal) }
        } else if (sessionOrders.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("🛍️", fontSize = 80.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Вы еще не делали заказ".tr(), color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Посмотрите новинки или все меню".tr(), color = GrayColor, fontSize = 14.sp, fontFamily = MasterFont)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateToMenu, modifier = Modifier.fillMaxWidth(0.6f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = CardColor), shape = RoundedCornerShape(12.dp)) { Text("МЕНЮ".tr(), color = TextColor, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
            }
        } else {
            // Рисуем список из нашей мгновенной памяти!
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessionOrders) { order -> OrderCard(order) }
            }
        }
    }
}

@Composable
fun OrderCard(order: OrderHistoryItem) {
    Box(modifier = Modifier.fillMaxWidth().background(CardColor, shape = RoundedCornerShape(16.dp)).padding(16.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                // Номер заказа и дата
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "ЗАКАЗ".tr() + " " + order.orderNumber, color = TextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = order.date.tr(), color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont)
                }
                Spacer(modifier = Modifier.width(8.dp))

                // === АККУРАТНЫЙ И МАЛЕНЬКИЙ СТАТУС ===
                Box(
                    modifier = Modifier
                        .background(if (order.isActive) MasterTeal.copy(alpha = 0.15f) else BgColor, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp) // Уменьшили отступы
                ) {
                    Text(
                        text = order.status.tr(),
                        color = if (order.isActive) MasterTeal else GrayColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, // Уменьшили шрифт
                        fontFamily = MasterFont,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            // Тонкая линия разделитель для красоты
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GrayColor.copy(alpha = 0.1f)))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                // === ПОЛНОЕ ОПИСАНИЕ (УБРАЛИ maxLines) ===
                Text(
                    text = order.itemsDescription.trOrderDetails(),
                    color = GrayColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    fontFamily = MasterFont,
                    lineHeight = 20.sp
                )
                Text(text = "${order.price} ₸", color = TextColor, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = MasterFont)
            }
        }
    }
}

@Composable
fun MasterBottomSheetDialog(title: String, placeholder: String, initialText: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(horizontal = 20.dp, vertical = 20.dp)) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).background(GrayColor.copy(alpha = 0.3f), CircleShape).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // title уже переведен заранее
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextColor, fontFamily = MasterFont)
                    Box(modifier = Modifier.size(32.dp).background(BgColor, CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("✕", color = GrayColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
                }
                Spacer(modifier = Modifier.height(20.dp))
                // placeholder тоже уже переведен заранее
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text(placeholder, color = GrayColor, fontFamily = MasterFont) }, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = BgColor, focusedContainerColor = BgColor, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent, focusedTextColor = TextColor, unfocusedTextColor = TextColor), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MasterFont))
                Spacer(modifier = Modifier.height(24.dp))
                // Переводим кнопку
                Button(onClick = { onApply(text) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MasterTeal), shape = RoundedCornerShape(16.dp)) { Text("ПРИМЕНИТЬ".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont) }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ShopDetailsDialog(shop: CoffeeShop, onDismiss: () -> Unit, onOrderHere: (CoffeeShop) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    Image(painter = painterResource(shop.imageRes), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp).background(Color.White, CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }

                Column(modifier = Modifier.fillMaxWidth().background(CardColor).padding(20.dp)) {
                    Text(shop.address, fontSize = 24.sp, color = TextColor, fontWeight = FontWeight.Medium, fontFamily = MasterFont)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(shop.address, fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Column { Text("График работы".tr(), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = MasterFont); Text(shop.workingHours, color = GrayColor, fontSize = 14.sp, fontFamily = MasterFont) } }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp)); Column { Text("Контакты".tr(), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = MasterFont); Text(shop.phone, color = GrayColor, fontSize = 14.sp, fontFamily = MasterFont) } }
                        Box(modifier = Modifier.background(MasterTeal.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) { Text("Позвонить".tr(), color = MasterTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
                    }
                }
            }
            Button(onClick = { onOrderHere(shop) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = MasterTeal), shape = RoundedCornerShape(20.dp)) { Text("Заказать здесь".tr(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont) }
        }
    }
}

@Composable
fun ShopSelectionDialog(onDismiss: () -> Unit, onShopSelect: (CoffeeShop) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedShopForDetails by remember { mutableStateOf<CoffeeShop?>(null) }
    val filteredShops = remember(searchQuery) { if (searchQuery.isBlank()) masterCoffeeShops else masterCoffeeShops.filter { it.address.contains(searchQuery, ignoreCase = true) } }

    if (selectedShopForDetails != null) {
        ShopDetailsDialog(shop = selectedShopForDetails!!, onDismiss = { selectedShopForDetails = null }, onOrderHere = { shop -> onShopSelect(shop) })
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(modifier = Modifier.fillMaxSize().background(CardColor)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад", tint = TextColor) }
                    Text("Выбор кофейни".tr(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextColor, fontFamily = MasterFont)
                    Box(modifier = Modifier.size(36.dp).background(BgColor, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Map, contentDescription = "Карта", tint = TextColor, modifier = Modifier.size(20.dp)) }
                }

                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск".tr(), color = GrayColor, fontFamily = MasterFont) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GrayColor) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = BgColor, focusedContainerColor = BgColor, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent, focusedTextColor = TextColor, unfocusedTextColor = TextColor),
                    singleLine = true
                )

                Text("АСТАНА".tr(), color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredShops) { shop ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedShopForDetails = shop }.padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text(shop.address, color = TextColor, fontSize = 16.sp, fontFamily = MasterFont); Spacer(modifier = Modifier.height(4.dp)); Text(shop.status.tr(), color = GrayColor.copy(alpha = 0.7f), fontSize = 14.sp, fontFamily = MasterFont) }
                            Icon(imageVector = Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        }
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(BgColor))
                    }
                }
            }
        }
    }
}
// === ДАННЫЕ ДЛЯ ДОБАВОК ===
data class AddonItem(val name: String, val price: Int = 0)

val dummySyrups = listOf(AddonItem("Без сиропа"), AddonItem("Сироп Карамель", 0), AddonItem("Сироп Соленая Карамель", 0), AddonItem("Сироп Ваниль", 0))
val dummyEssences = listOf(AddonItem("Без эссенции"), AddonItem("Амаретто эссенция", 60), AddonItem("Вишня эссенция", 60), AddonItem("Груша эссенция", 60))
val dummyMilks = listOf(AddonItem("Без молока"), AddonItem("Миндальное молоко", 0), AddonItem("Кокосовое молоко", 0), AddonItem("Фундучное молоко", 0))

@Composable
fun ProductOrderDialog(
    product: CoffeeProduct,
    onDismiss: () -> Unit,
    onAddToCart: (size: CoffeeSize, sugar: Int, type: String, temp: String, finalPrice: Int, addonsText: String) -> Unit
) {
    var selectedSize by remember { mutableStateOf(product.sizes.first()) }
    var sugarLevel by remember { mutableStateOf(0f) }
    var orderType by remember { mutableStateOf("На вынос") }
    var temperature by remember { mutableStateOf("Стандарт") }

    var selectedSyrups by remember { mutableStateOf(setOf(dummySyrups.first())) }
    var selectedEssences by remember { mutableStateOf(setOf(dummyEssences.first())) }
    var selectedMilks by remember { mutableStateOf(setOf(dummyMilks.first())) }

    val totalPrice = selectedSize.price + selectedSyrups.sumOf { it.price } + selectedEssences.sumOf { it.price } + selectedMilks.sumOf { it.price }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor)) {

            Column(modifier = Modifier.fillMaxSize()) {

                // === ПРОКРУЧИВАЕМАЯ ЧАСТЬ (КАРТИНКА + НАСТРОЙКИ) ===
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

                    // --- 1. ШАПКА С КАРТИНКОЙ ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(CardColor, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)) // Красивое закругление снизу
                            .shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp), spotColor = Color.Black.copy(alpha = 0.05f))
                    ) {
                        // Картинка напитка (крупно по центру)
                        Image(
                            painter = painterResource(product.imageRes),
                            contentDescription = product.name.tr(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp, bottom = 24.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Кнопка Закрыть в правом верхнем углу поверх картинки
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 24.dp, end = 20.dp)
                                .size(40.dp)
                                .background(BgColor, CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    // --- 2. КОНТЕНТ (НАЗВАНИЕ, ОПИСАНИЕ, НАСТРОЙКИ) ---
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(modifier = Modifier.height(24.dp))

                        // Название напитка
                        Text(
                            text = product.name.tr(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TextColor,
                            fontFamily = MasterFont
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Описание напитка (оно у нас уже переведено в словаре!)
                        Text(
                            text = product.description.tr(),
                            fontSize = 15.sp,
                            color = GrayColor.copy(alpha = 0.8f),
                            fontFamily = MasterFont,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // === ВЫБОР ОБЪЕМА ===
                        Text("ОБЪЕМ".tr(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            product.sizes.forEach { size ->
                                val isSelected = size == selectedSize
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp)
                                        .background(CardColor, RoundedCornerShape(16.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MasterTeal else GrayColor.copy(alpha=0.2f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedSize = size },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.LocalCafe, contentDescription = null, tint = if (isSelected) MasterTeal else GrayColor.copy(alpha=0.5f), modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("${size.price} ₸", color = TextColor, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontFamily = MasterFont)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(size.volume, color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // === САХАР ===
                        Box(modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(16.dp)).padding(16.dp)) {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("САХАР".tr(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
                                    Icon(Icons.Outlined.Info, contentDescription = null, tint = GrayColor.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Slider(value = sugarLevel, onValueChange = { sugarLevel = it }, valueRange = 0f..5f, steps = 4, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = MasterTeal, activeTrackColor = MasterTeal, inactiveTrackColor = GrayColor.copy(alpha=0.2f)))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (0..5).forEach { Text("$it", color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont) } }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // === ЗАКАЗ (На вынос / В зале) ===
                        Text("ЗАКАЗ".tr(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelectableOrderCard(modifier = Modifier.weight(1f), text = "На вынос".tr(), icon = Icons.Outlined.ShoppingBag, isSelected = orderType == "На вынос") { orderType = "На вынос" }
                            SelectableOrderCard(modifier = Modifier.weight(1f), text = "В зале".tr(), icon = Icons.Outlined.LocalCafe, isSelected = orderType == "В зале") { orderType = "В зале" }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // === ДОБАВКИ ===
                        ExpandableAddonSection(title = "СИРОПЫ".tr(), subtitle = "Выберите минимум 1 позицию".tr(), items = dummySyrups, selectedItems = selectedSyrups) { item -> selectedSyrups = setOf(item) }
                        ExpandableAddonSection(title = "ЭССЕНЦИИ БЕЗ САХАРА".tr(), subtitle = "Выберите до 3 дополнительных товаров".tr(), items = dummyEssences, selectedItems = selectedEssences) { item ->
                            val newSet = selectedEssences.toMutableSet()
                            if (item.name.startsWith("Без ")) selectedEssences = setOf(item)
                            else {
                                newSet.removeAll { it.name.startsWith("Без ") }
                                if (newSet.contains(item)) { newSet.remove(item); selectedEssences = if (newSet.isEmpty()) setOf(dummyEssences[0]) else newSet }
                                else { if (newSet.size >= 3) newSet.remove(newSet.first()); newSet.add(item); selectedEssences = newSet }
                            }
                        }
                        ExpandableAddonSection(title = "МОЛОКО".tr(), subtitle = "Выберите до 2 дополнительных товаров".tr(), items = dummyMilks, selectedItems = selectedMilks) { item ->
                            val newSet = selectedMilks.toMutableSet()
                            if (item.name.startsWith("Без ")) selectedMilks = setOf(item)
                            else {
                                newSet.removeAll { it.name.startsWith("Без ") }
                                if (newSet.contains(item)) { newSet.remove(item); selectedMilks = if (newSet.isEmpty()) setOf(dummyMilks[0]) else newSet }
                                else { if (newSet.size >= 2) newSet.remove(newSet.first()); newSet.add(item); selectedMilks = newSet }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // === ТЕМПЕРАТУРА ===
                        Text("ТЕМПЕРАТУРА".tr(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelectableOrderCard(modifier = Modifier.weight(1f), text = "Стандарт".tr(), icon = Icons.Outlined.DeviceThermostat, isSelected = temperature == "Стандарт") { temperature = "Стандарт" }
                            SelectableOrderCard(modifier = Modifier.weight(1f), text = "Горячее".tr(), icon = Icons.Outlined.LocalFireDepartment, isSelected = temperature == "Горячее") { temperature = "Горячее" }
                            SelectableOrderCard(modifier = Modifier.weight(1f), text = "Холоднее".tr(), icon = Icons.Outlined.AcUnit, isSelected = temperature == "Холоднее") { temperature = "Холоднее" }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // === НИЖНЯЯ КНОПКА КОРЗИНЫ (ПРИЛИПАЕТ К НИЗУ) ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgColor)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    val addonsListRaw = (selectedSyrups + selectedEssences + selectedMilks)
                        .filter { !it.name.startsWith("Без ") }
                        .joinToString(", ") { it.name }

                    Button(
                        onClick = {
                            onAddToCart(selectedSize, sugarLevel.toInt(), orderType, temperature, totalPrice, addonsListRaw)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("В корзину за ".tr() + "$totalPrice ₸", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableAddonSection(title: String, subtitle: String, items: List<AddonItem>, selectedItems: Set<AddonItem>, onItemToggle: (AddonItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Text(title.tr(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextColor, fontFamily = MasterFont)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle.tr(), fontSize = 12.sp, color = GrayColor, fontFamily = MasterFont)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = GrayColor.copy(alpha=0.5f), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = GrayColor.copy(alpha=0.5f), modifier = Modifier.size(20.dp))
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                val isSelected = selectedItems.contains(item)
                Row(modifier = Modifier.fillMaxWidth().clickable { onItemToggle(item) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = if (isSelected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = if (isSelected) MasterTeal else GrayColor.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))

                    // Самое важное: переводим название самой добавки в списке!
                    Text(item.name.tr(), color = TextColor, fontSize = 14.sp, fontFamily = MasterFont)

                    if (item.price > 0) { Spacer(modifier = Modifier.width(8.dp)); Text("+${item.price} ₸", color = GrayColor.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = MasterFont) }
                }
            }
        }
    }
}

@Composable
fun SelectableOrderCard(modifier: Modifier = Modifier, text: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(90.dp).background(CardColor, RoundedCornerShape(16.dp)).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) MasterTeal else Color.Transparent, shape = RoundedCornerShape(16.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = if (isSelected) MasterTeal else GrayColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text.tr(), color = TextColor, fontSize = 13.sp, fontFamily = MasterFont, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var isAnimating by remember { mutableStateOf(false) }

    val alphaAnimation by animateFloatAsState(targetValue = if (isAnimating) 1f else 0f, animationSpec = tween(durationMillis = 1500))
    val scaleAnimation by animateFloatAsState(targetValue = if (isAnimating) 1f else 0.5f, animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing))

    LaunchedEffect(Unit) { isAnimating = true; delay(2500); onTimeout() }

    Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(Res.drawable.master_logo), contentDescription = "Логотип MASTER COFFEE".tr(), modifier = Modifier.size(450.dp).scale(scaleAnimation).alpha(alphaAnimation))
        }
    }
}

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 10) text.text.substring(0..9) else text.text
        var out = "+7 ("
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 2) out += ") "
            if (i == 5) out += "-"
            if (i == 7) out += "-"
        }
        val offsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 4
                if (offset <= 2) return 4 + offset
                if (offset == 3) return 9
                if (offset <= 5) return 6 + offset
                if (offset == 6) return 13
                if (offset <= 7) return 7 + offset
                if (offset == 8) return 16
                if (offset <= 10) return 8 + offset
                return 18
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return 0
                if (offset <= 7) return offset - 4
                if (offset <= 9) return 3
                if (offset <= 12) return offset - 6
                if (offset <= 13) return 6
                if (offset <= 15) return offset - 7
                if (offset <= 16) return 8
                if (offset <= 18) return offset - 8
                return 10
            }
        }
        return TransformedText(AnnotatedString(out), offsetTranslator)
    }
}

@Composable
fun LoyaltyScreen(userName: String, onBackClick: () -> Unit, onOpenQr: () -> Unit) {
    val settings = remember { Settings() }
    var orderCount by remember { mutableStateOf(settings.getInt("cached_order_count", 0)) }

    LaunchedEffect(Unit) {
        try {
            val freshCount = dev.gitlive.firebase.Firebase.firestore.collection("orders").get().documents.count { it.get<Boolean>("isActive") == false }
            if (orderCount != freshCount) {
                orderCount = freshCount
                settings.putInt("cached_order_count", freshCount)
            }
        } catch (e: Exception) { println(e.message) }
    }

    val currentDiscount: Int
    val nextDiscount: Int
    val ordersNeeded: Int
    val progressFraction: Float

    when {
        orderCount < 50 -> { currentDiscount = 3; nextDiscount = 5; ordersNeeded = 50 - orderCount; progressFraction = orderCount.toFloat() / 50f }
        orderCount < 70 -> { currentDiscount = 5; nextDiscount = 7; ordersNeeded = 70 - orderCount; progressFraction = (orderCount - 50).toFloat() / 20f }
        orderCount < 100 -> { currentDiscount = 7; nextDiscount = 10; ordersNeeded = 100 - orderCount; progressFraction = (orderCount - 70).toFloat() / 30f }
        else -> { currentDiscount = 10; nextDiscount = 10; ordersNeeded = 0; progressFraction = 1f }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgColor).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Назад".tr(), tint = TextColor) }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "СИСТЕМА ЛОЯЛЬНОСТИ".tr(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth().height(220.dp).shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))) {
            Image(painter = painterResource(Res.drawable.card_bg), contentDescription = "Фон".tr(), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("КАРТА КОФЕМАНА".tr(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = MasterFont, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        // Отделяем текст от процента
                        Text("Ваша скидка: ".tr() + "$currentDiscount%", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = MasterFont)
                    }
                    Text("$currentDiscount%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp, fontFamily = MasterFont)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (ordersNeeded > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("До скидки ".tr() + "$nextDiscount%", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = MasterFont)
                            Text("Осталось заказов: ".tr() + "$ordersNeeded", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = MasterFont)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Text("Достигнута максимальная скидка!".tr(), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = MasterFont) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = maxOf(0.02f, progressFraction)).fillMaxHeight().background(Color.White, CircleShape))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(userName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, fontFamily = MasterFont, modifier = Modifier.weight(1f))
                    Button(onClick = { onOpenQr() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("МОЙ QR".tr(), color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MasterFont)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "УРОВНИ СКИДОК".tr(), color = GrayColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DiscountBadge(percent = "3", isActive = currentDiscount >= 3, subtitle = "3%")
            DiscountBadge(percent = "5", isActive = currentDiscount >= 5, subtitle = if (currentDiscount == 3) "5%\n$orderCount/50" else "5%")
            DiscountBadge(percent = "7", isActive = currentDiscount >= 7, subtitle = if (currentDiscount == 5) "7%\n$orderCount/70" else "7%")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            DiscountBadge(percent = "10", isActive = currentDiscount >= 10, subtitle = if (currentDiscount == 7) "10%\n$orderCount/100" else "10%")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "КАК ПОЛУЧИТЬ МАКСИМАЛЬНУЮ СКИДКУ?*".tr(), color = GrayColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
        Spacer(modifier = Modifier.height(12.dp))
        // Переводим весь большой блок текста
        Text(text = "Каждая отдельная покупка** кофейного или чайного напитка приближает вас к 10% скидке:\n\n• 50 и более чеков — повышение до скидки 5%;\n• 70 и более чеков — повышение до скидки 7%;\n• 100 и более чеков — максимальная скидка 10%.\n\n*Покупка для повышения уровня засчитывается только при наличии напитка в чеке.\n**Скидка действует только на напитки.".tr(), color = TextColor, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = MasterFont)
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun DiscountBadge(percent: String, isActive: Boolean, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Box(
            modifier = Modifier.size(70.dp).clip(CircleShape).background(BgColor).border(width = if (isActive) 3.dp else 1.dp, color = if (isActive) MasterTeal else GrayColor.copy(alpha = 0.3f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(text = percent, fontSize = 32.sp, fontWeight = FontWeight.Black, color = if (isActive) MasterTeal else GrayColor.copy(alpha = 0.5f), fontFamily = MasterFont) }
        Spacer(modifier = Modifier.height(8.dp))
        // На всякий случай добавили .tr() для подзаголовка
        Text(text = subtitle.tr(), color = if (isActive) TextColor else GrayColor, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontFamily = MasterFont, textAlign = TextAlign.Center)
    }
}

// === УНИВЕРСАЛЬНЫЙ ШАБЛОН ДЛЯ ПОЛНОЦЕННЫХ ЭКРАНОВ ===
@Composable
fun ProfileSubScreen(title: String, onBackClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BgColor).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Переводим системное описание кнопки "Назад"
            IconButton(onClick = onBackClick, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Назад".tr(), tint = TextColor) }
            Spacer(modifier = Modifier.width(16.dp))
            // title приходит сюда уже переведенным, просто делаем его капсом
            Text(text = title.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

// === НОВЫЙ КОМПОНЕНТ ДЛЯ ЭКРАНА ПРИГЛАШЕНИЯ ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsContent() {
    // 1. Берем встроенный инструмент работы с буфером обмена
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val shareManager = rememberShareManager()

    // 2. Переменные состояния для Тоста и Шторки
    var showCopiedToast by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }

    val promoCode = "KAIRBEK-2026"

    // Обернули в Box, чтобы Тост и Шторка могли рисоваться поверх экрана
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // --- 1. ИЛЛЮСТРАЦИЯ ---
            Image(
                painter = painterResource(Res.drawable.invite_illustration), // <- Твоя картинка
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Пригласи друга\nи получи 50 MC".tr(),
                color = TextColor, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                fontFamily = MasterFont, textAlign = TextAlign.Center, lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "после первой покупки кофе другом".tr(),
                color = GrayColor, fontSize = 14.sp, fontFamily = MasterFont, textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- 4. БЛОК ПРОМОКОДА ---
            Box(
                modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(16.dp)).border(1.dp, GrayColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)).padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("твой промокод".tr(), color = GrayColor, fontSize = 12.sp, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(promoCode, color = MasterTeal, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = MasterFont)
                    }

                    // === МАГИЯ КОПИРОВАНИЯ ===
                    IconButton(
                        onClick = {
                            // Копируем текст в память телефона
                            clipboardManager.setText(AnnotatedString(promoCode))
                            // Показываем уведомление на 2 секунды
                            scope.launch {
                                showCopiedToast = true
                                delay(2000)
                                showCopiedToast = false
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "Копировать".tr(), tint = GrayColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))


            // --- 5. ГЛАВНАЯ КНОПКА "ПОДЕЛИТЬСЯ" ---
            Button(
                onClick = {
                    // Формируем красивое рекламное сообщение, отделяя текст от переменной
                    val shareMessage = "Привет! Скачивай Master Coffee и вводи мой промокод ".tr() + promoCode + " при регистрации! Мы оба получим по 50 MC на баланс ☕\n\nСкачать: https://mastercoffee.kz/app/invite?code=".tr() + promoCode
                    // Запускаем нативное меню!
                    shareManager.shareText(shareMessage)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Поделиться промокодом".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 6. КНОПКА "КАК ЭТО РАБОТАЕТ" ---
            TextButton(onClick = { showHowItWorks = true }) {
                Text("Показать как это работает".tr(), color = MasterTeal, fontWeight = FontWeight.Medium, fontSize = 14.sp, fontFamily = MasterFont)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // ==========================================
        // === ВСПЛЫВАЮЩЕЕ УВЕДОМЛЕНИЕ (ТОСТ) ===
        // ==========================================
        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp) // Подняли над кнопками
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(Color(0xFF333333), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Промокод скопирован!".tr(), color = Color.White, fontFamily = MasterFont, fontSize = 14.sp)
            }
        }

        // ==========================================
        // === ШТОРКА "КАК ЭТО РАБОТАЕТ" ===
        // ==========================================
        if (showHowItWorks) {
            ModalBottomSheet(
                onDismissRequest = { showHowItWorks = false },
                containerColor = CardColor,
                scrimColor = Color.Black.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {
                    Text("Как работает система?".tr(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
                    Spacer(modifier = Modifier.height(24.dp))

                    Row {
                        Text("1.", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вы отправляете промокод другу любым удобным способом.".tr(), color = TextColor, fontSize = 14.sp, fontFamily = MasterFont, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Text("2.", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Друг скачивает приложение и вводит ваш код при регистрации или в корзине.".tr(), color = TextColor, fontSize = 14.sp, fontFamily = MasterFont, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Text("3.", color = MasterTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("После того, как друг оплатит свой первый заказ, вы оба получите по 50 MC на баланс!".tr(), color = TextColor, fontSize = 14.sp, fontFamily = MasterFont, lineHeight = 20.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { showHowItWorks = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("ПОНЯТНО".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                    }
                }
            }
        }
    }
}
// === НОВЫЙ КОМПОНЕНТ ДЛЯ ЭКРАНА "ЛИЧНЫЕ ДАННЫЕ" ===
// === ОБНОВЛЕННЫЙ КОМПОНЕНТ "ЛИЧНЫЕ ДАННЫЕ" ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDataContent(
    currentName: String,
    currentEmail: String,
    currentId: String,
    currentAvatarKey: String?, // Принимаем текущий ключ аватара
    onSave: (String, String, String?) -> Unit // Передаем ключ обратно при сохранении
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    // Состояние выбранного аватара
    var selectedAvatarKey by remember { mutableStateOf(currentAvatarKey) }
    // Состояние выезжающей шторки
    var showAvatarSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        // === ПРЕМИАЛЬНЫЙ АВАТАР С КАРАНДАШОМ (КАК У ZEBRA) ===
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                // Сам аватар
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (selectedAvatarKey == null) MasterTeal.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { showAvatarSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedAvatarKey != null && avatarMap.containsKey(selectedAvatarKey)) {
                        Image(
                            painter = painterResource(avatarMap[selectedAvatarKey]!!),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = currentName.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MasterTeal, fontFamily = MasterFont)
                    }
                }

                // Иконка редактирования (карандашик)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(BgColor, CircleShape) // Обводка цвета фона, чтобы не сливалось
                        .padding(2.dp)
                        .background(TextColor, CircleShape)
                        .clickable { showAvatarSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = BgColor, modifier = Modifier.size(16.dp))
                }
            }
        }



        // Плашка с ID (Вернули как было!)
        // --- ДАЛЬШЕ ИДЕТ КОД ПОЛЕЙ ВВОДА ---
        Row(
            modifier = Modifier.fillMaxWidth().background(MasterTeal.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ваш уникальный ID".tr(),
                color = TextColor,
                fontWeight = FontWeight.Bold,
                fontFamily = MasterFont,
                // ДАЕМ ВЕС: Этот текст займет оставшееся место, не выдавливая ID
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp)) // Добавляем жесткий отступ между текстами
            Text(
                text = currentId,
                color = MasterTeal,
                fontWeight = FontWeight.Black,
                fontFamily = MasterFont,
                fontSize = 18.sp,
                maxLines = 1 // ЖЕЛЕЗНО запрещаем перенос на новую строку
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ваше имя".tr(), color = GrayColor, fontFamily = MasterFont) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MasterTeal, unfocusedBorderColor = GrayColor.copy(alpha=0.3f), focusedTextColor = TextColor, unfocusedTextColor = TextColor), singleLine = true)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email адрес".tr(), color = GrayColor, fontFamily = MasterFont) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MasterTeal, unfocusedBorderColor = GrayColor.copy(alpha=0.3f), focusedTextColor = TextColor, unfocusedTextColor = TextColor), singleLine = true)
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onSave(name, email, selectedAvatarKey) }, // СОХРАНЯЕМ!
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MasterTeal), shape = RoundedCornerShape(16.dp)
        ) { Text("СОХРАНИТЬ ИЗМЕНЕНИЯ".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont) }
    }

    // === ШТОРКА ВЫБОРА АВАТАРА ===
    if (showAvatarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            containerColor = CardColor,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp)) {

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("АВАТАР".tr(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)
                    Box(modifier = Modifier.size(32.dp).background(BgColor, CircleShape).clickable { showAvatarSheet = false }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Сетка 3 колонки
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(avatarMap.keys.toList()) { key ->
                        val res = avatarMap[key]!!
                        val isSelected = key == selectedAvatarKey

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f) // Делает квадратным
                                .clip(CircleShape)
                                .background(if (isSelected) MasterTeal.copy(alpha = 0.1f) else Color.Transparent)
                                .border(if (isSelected) 2.dp else 0.dp, MasterTeal, CircleShape)
                                .clickable {
                                    selectedAvatarKey = key
                                    showAvatarSheet = false // Закрываем при выборе!
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(res),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(if (isSelected) 4.dp else 0.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun AboutUsContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // --- 1. ЛОГОТИП В ФИРМЕННОМ КВАДРАТЕ (Как в примере, но Teal!) ---
        Box(
            modifier = Modifier
                .size(160.dp)
                // === МАГИЯ: ПОМЕНЯЛИ ЦВЕТ С ЧЕРНОГО НА НАШ Teal ===
                .background(MasterTeal, RoundedCornerShape(36.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                // Берем твой квадратный логотип (который используется в QR-коде)
                painter = painterResource(Res.drawable.master_logo2),
                contentDescription = "Логотип".tr(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 2. ЗАГОЛОВОК (Остается на месте) ---
        Text(
            text = "MASTER COFFEE".tr(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black, // Самый жирный шрифт
            color = MasterTeal,
            fontFamily = MasterFont,
            letterSpacing = 1.5.sp // Расстояние между буквами для солидности
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. ОПИСАНИЕ ИЗ ИНТЕРНЕТА (Остается на месте) ---
        Text(
            text = "Казахстанская сеть specialty-кофеен, основанная в 2014 году в Астане.\n\nМы не просто варим кофе — мы развиваем кофейную культуру. У нас собственная высокотехнологичная обжарка зерен, школа бариста и десятки филиалов с уникальной атмосферой по всей стране.\n\nНаша главная миссия — делать премиальный спешелти-кофе доступным на каждый день!".tr(),
            fontSize = 14.sp,
            color = TextColor,
            fontFamily = MasterFont,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp // Отступ между строками, чтобы легко читалось
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartQrBottomSheet(
    finalPrice: Int,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    // Генерируем красивый случайный код заказа
    val uniqueCode = remember { "${(100..999).random()}-${(100..999).random()}" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgColor,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // МАГИЯ: Железный отступ снизу, чтобы шторка не срезала кнопки и текст!
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- ШАПКА ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(32.dp)) // Пустышка для центрирования текста
                Text("ОПЛАТА НА КАССЕ".tr(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)

                // Круглая кнопка "Закрыть"
                Box(
                    modifier = Modifier.size(32.dp).background(CardColor, CircleShape).clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- КАРТОЧКА С QR-КОДОМ ---
            Column(
                modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(24.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val qrPainter = rememberQrCodePainter(
                    data = "ORDER-$uniqueCode",
                    darkBrush = QrBrush.solid(MasterTeal),
                    lightBrush = QrBrush.solid(Color.Transparent),
                    logoPainter = ColorPainter(Color.Transparent)
                )

                Box(
                    modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = qrPainter, contentDescription = "QR-код", modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier.size(44.dp).background(MasterTeal, RoundedCornerShape(10.dp)).padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(Res.drawable.master_logo2), contentDescription = "Лого", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Покажите QR кассиру для оплаты".tr(), fontSize = 14.sp, color = TextColor, fontFamily = MasterFont)

                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GrayColor.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.height(24.dp))

                Text(uniqueCode, fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextColor, fontFamily = MasterFont, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ваш уникальный код".tr(), fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- КАРТОЧКА С СУММОЙ ---
            Column(
                modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(24.dp)).padding(24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ваша скидка".tr(), fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
                    Text("-3%", fontSize = 14.sp, color = MasterTeal, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GrayColor.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.height(16.dp))

                // Исправленная строка Итого
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Итого к оплате".tr(),
                        color = TextColor,
                        fontSize = 16.sp,
                        fontFamily = MasterFont,
                        modifier = Modifier.weight(1f),
                        lineHeight = 22.sp // Даем тексту место при переносе
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "$finalPrice ₸",
                        color = TextColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        fontFamily = MasterFont,
                        maxLines = 1 // Запрещаем отрывать тенге
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- КНОПКА ---
            Button(
                onClick = onSuccess,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MasterTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Кассир отсканировал".tr(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = MasterFont)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQrBottomSheet(
    userName: String,
    onDismiss: () -> Unit
) {
    val uniqueCode = remember { "${(100..999).random()}-${(100..999).random()}" }

    // ==========================================================
    // === МАГИЯ ЗДЕСЬ: ПОДТЯГИВАЕМ РЕАЛЬНЫЕ ДАННЫЕ ИЗ БАЗЫ ===
    // ==========================================================
    var orderCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            // Считаем количество завершенных заказов из Firebase (как на Главном экране)
            orderCount = dev.gitlive.firebase.Firebase.firestore.collection("orders")
                .get().documents.count { it.get<Boolean>("isActive") == false }
        } catch (e: Exception) {
            println("Ошибка загрузки заказов для QR: ${e.message}")
        }
    }

    // Высчитываем актуальную скидку по нашей бизнес-логике
    val currentDiscount = when {
        orderCount < 50 -> 3
        orderCount < 70 -> 5
        orderCount < 100 -> 7
        else -> 10
    }
    // ==========================================================

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgColor,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- ШАПКА ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(32.dp))
                Text("МОЙ QR-КОД".tr(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextColor, fontFamily = MasterFont)

                Box(
                    modifier = Modifier.size(32.dp).background(CardColor, CircleShape).clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть", tint = TextColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- КАРТОЧКА С QR-КОДОМ ---
            Column(
                modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(24.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val qrPainter = rememberQrCodePainter(
                    data = "USER-$uniqueCode",
                    darkBrush = QrBrush.solid(MasterTeal),
                    lightBrush = QrBrush.solid(Color.Transparent),
                    logoPainter = ColorPainter(Color.Transparent),
                    logoPadding = QrLogoPadding.Natural(0.1f),
                    logoShape = QrLogoShape.circle(),
                    logoSize = 0.22f
                )
                Box(
                    modifier = Modifier.size(200.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = qrPainter, contentDescription = "QR-код", modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier.size(44.dp).background(MasterTeal, RoundedCornerShape(10.dp)).padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(Res.drawable.master_logo2), contentDescription = "Лого", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Покажите QR для начисления бонусов".tr(), fontSize = 14.sp, color = TextColor, fontFamily = MasterFont, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GrayColor.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.height(24.dp))

                Text(uniqueCode, fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextColor, fontFamily = MasterFont, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ваш уникальный код".tr(), fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- КАРТОЧКА СО СТАТИСТИКОЙ ---
            Column(
                modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(24.dp)).padding(24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SentimentSatisfied, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Ваша скидка".tr(), fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
                    }
                    // ВСТАВИЛИ ДИНАМИЧЕСКУЮ СКИДКУ
                    Text("${currentDiscount}%", fontSize = 16.sp, color = MasterTeal, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GrayColor.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalCafe, contentDescription = null, tint = MasterTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Кофе на балансе".tr(), fontSize = 14.sp, color = GrayColor, fontFamily = MasterFont)
                    }
                    // ВСТАВИЛИ ДИНАМИЧЕСКОЕ КОЛИЧЕСТВО ЧАШЕК
                    Text("$orderCount", fontSize = 16.sp, color = MasterTeal, fontWeight = FontWeight.Bold, fontFamily = MasterFont)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StoryViewerScreen(
    stories: List<FirebaseStory>, // Принимаем список картинок
    initialIndex: Int,            // С какой начинаем
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    var isPaused = remember { mutableStateOf(false) }
    var currentTimeMillis by remember { mutableStateOf(0L) }
    var progress by remember { mutableStateOf(0f) }

    val totalDurationMillis = 5000L
    val frameDelayMillis = 10L

    // Перезапускаем таймер каждый раз, когда меняется currentIndex
    LaunchedEffect(currentIndex) {
        currentTimeMillis = 0L
        progress = 0f
        while (currentTimeMillis < totalDurationMillis) {
            if (isPaused.value) {
                delay(frameDelayMillis)
                continue
            }
            delay(frameDelayMillis)
            currentTimeMillis += frameDelayMillis
            progress = currentTimeMillis.toFloat() / totalDurationMillis.toFloat()
        }

        // Время вышло: идем дальше или закрываем
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    // ТРЕКЕР ДЛЯ СВАЙПА ВНИЗ
    val velocityTracker = remember { VelocityTracker() }

    // Box с фоном темы приложения (BgColor)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor) // МАГИЯ: Фон подстраивается под тему!
            // === 1. ОБРАБОТКА ТАПОВ И УДЕРЖАНИЯ ===
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Пауза при удержании
                        isPaused.value = true
                        try { awaitRelease() } finally { isPaused.value = false }
                    },
                    onTap = { offset ->
                        // Перелистывание по тапу!
                        val isRightHalf = offset.x > size.width / 2
                        if (isRightHalf) {
                            if (currentIndex < stories.size - 1) currentIndex++ else onDismiss()
                        } else {
                            if (currentIndex > 0) currentIndex--
                            else { currentTimeMillis = 0L; progress = 0f } // Сброс таймера первой сторис
                        }
                    }
                )
            }
            // === 2. ОБРАБОТКА СВАЙПА ВНИЗ ДЛЯ ЗАКРЫТИЯ ===
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDrag = { change, dragAmount ->
                        // Отслеживаем движение только если свайп идет ВНИЗ (y > 0)
                        // и вертикальное движение больше горизонтального
                        if (dragAmount.y > 0 && kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity()
                        // Если юзер резко смахнул вниз (скорость > 800) — закрываем сторис!
                        if (velocity.y > 800) {
                            onDismiss()
                        }
                    }
                )
            }
    ) {

        // === ГЛАВНАЯ КАРТИНКА (ВМЕСТО Crossfade + Image) ===
        Crossfade(targetState = currentIndex, modifier = Modifier.fillMaxSize()) { page ->
            // Обернули в Box, чтобы отцентрировать картинку по вертикали
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                coil3.compose.AsyncImage(
                    model = stories[page].url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth() // Занимаем всю доступную ширину
                        .aspectRatio(9f / 16f) // МАГИЯ: Жестко фиксируем формат 9 к 16!
                        .clip(RoundedCornerShape(16.dp)), // Инста-стайл: немного скругляем углы сторис
                    contentScale = ContentScale.Crop // Внутри этой рамки картинка заполняется идеально
                )
            }
        }

        // --- ВЕРХНИЙ СЛОЙ: УПРАВЛЕНИЕ (Прогресс-бары и крестик) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, bottom = 10.dp, start = 16.dp, end = 16.dp)
        ) {
            // СЕГМЕНТИРОВАННЫЙ ПРОГРЕСС-БАР (КАК В INSTAGRAM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { index, _ ->
                    val barProgress = when {
                        index < currentIndex -> 1f
                        index == currentIndex -> progress
                        else -> 0f
                    }

                    LinearProgressIndicator(
                        progress = barProgress,
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MasterTeal,
                        trackColor = GrayColor.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Крестик выравниваем по правому краю
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextColor, modifier = Modifier.size(24.dp))
            }
        }

        // === НИЖНИЙ СЛОЙ: МИНИАТЮРЫ (КАК В KASPI / INSTAGRAM) ===
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter) // Прижимаем к самому низу
                .fillMaxWidth()
                .padding(bottom = 32.dp), // Отступ от нижнего края экрана
            horizontalArrangement = Arrangement.Center, // Центрируем все миниатюры
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(stories) { index, story ->
                val isSelected = index == currentIndex

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(44.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // Бирюзовая рамка для активной сторис
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MasterTeal else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            // Переключаем сторис по клику на миниатюру
                            currentIndex = index
                        }
                ) {
                    coil3.compose.AsyncImage(
                        model = story.url,
                        contentDescription = "Миниатюра",
                        modifier = Modifier
                            .fillMaxSize()
                            // Затемняем неактивные миниатюры
                            .alpha(if (isSelected) 1f else 0.5f),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

// Наш сетевой клиент
val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
suspend fun uploadImageToCloudinary(imageBytes: ByteArray): String? {
    try {
        println("CLOUDINARY: Отправляем байты напрямую (Multipart)...")

        // === МАГИЯ ЗДЕСЬ: Пресет теперь прямо в URL! ===
        val url = "https://api.cloudinary.com/v1_1/dkkwjczuz/image/upload?upload_preset=master_coffee_preset"

        val response = httpClient.post(url) {
            setBody(MultiPartFormDataContent(
                formData {
                    // Здесь оставляем ТОЛЬКО саму картинку
                    append("file", imageBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"story.jpg\"")
                    })
                }
            ))
        }

        println("CLOUDINARY: Ждем ответ... Статус: ${response.status}")
        val responseBody = response.bodyAsText()
        println("CLOUDINARY: Тело ответа: $responseBody")

        val json = Json.parseToJsonElement(responseBody).jsonObject
        return json["secure_url"]?.jsonPrimitive?.content

    } catch (e: Exception) {
        println("CLOUDINARY: Сетевая ошибка: ${e.message}")
        e.printStackTrace()
        return null
    }
}

@Composable
fun AdminStoryUploader(
    onUploadComplete: () -> Unit
) {
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val imagePicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays ->
            println("АДМИНКА: 1. Галерея закрылась, получили результат!")
            val imageBytes = byteArrays.firstOrNull()

            if (imageBytes != null) {
                println("АДМИНКА: 2. Фотка успешно прочитана, размер: ${imageBytes.size} байт")
                isUploading = true

                scope.launch {
                    try {
                        println("АДМИНКА: 3. Начинаем отправку в Cloudinary...")
                        val uploadedUrl = uploadImageToCloudinary(imageBytes)

                        println("АДМИНКА: 4. Cloudinary вернул ссылку: $uploadedUrl")

                        if (uploadedUrl != null) {
                            val newStory = FirebaseStory(url = uploadedUrl, active = true)
                            Firebase.firestore.collection("stories").add(newStory)
                            println("АДМИНКА: 5. УСПЕХ! Документ создан в Firebase!")
                        } else {
                            println("АДМИНКА: ОШИБКА! Cloudinary вернул null")
                        }
                    } catch (e: Exception) {
                        println("АДМИНКА: КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
                        e.printStackTrace() // Выведет красную ошибку в логи
                    } finally {
                        isUploading = false
                        onUploadComplete()
                    }
                }
            } else {
                println("АДМИНКА: ОШИБКА! Галерея вернула пустоту (imageBytes == null)")
            }
        }
    )

    Button(
        onClick = {
            println("АДМИНКА: 0. Нажали кнопку выбора фото")
            imagePicker.launch()
        },
        enabled = !isUploading,
        colors = ButtonDefaults.buttonColors(containerColor = MasterTeal)
    ) {
        if (isUploading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text("➕ Добавить Сторис (Админ)", color = Color.White)
        }
    }
}