package kz.master_coffee.app.presentation
import androidx.compose.runtime.Composable
import mastercoffee.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
private val catalogTranslations: Map<String, List<StringResource>> = mapOf(
    "кофе" to listOf(Res.string.catalog_0_ru, Res.string.catalog_0_kk, Res.string.catalog_0_en),
    "чаи" to listOf(Res.string.catalog_1_ru, Res.string.catalog_1_kk, Res.string.catalog_1_en),
    "авторские" to listOf(Res.string.catalog_2_ru, Res.string.catalog_2_kk, Res.string.catalog_2_en),
    "холодные" to listOf(Res.string.catalog_3_ru, Res.string.catalog_3_kk, Res.string.catalog_3_en),
    "капучино" to listOf(Res.string.catalog_4_ru, Res.string.catalog_4_kk, Res.string.catalog_4_en),
    "латте" to listOf(Res.string.catalog_5_ru, Res.string.catalog_5_kk, Res.string.catalog_5_en),
    "эспрессо" to listOf(Res.string.catalog_6_ru, Res.string.catalog_6_kk, Res.string.catalog_6_en),
    "американо" to listOf(Res.string.catalog_7_ru, Res.string.catalog_7_kk, Res.string.catalog_7_en),
    "безсиропа" to listOf(Res.string.catalog_8_ru, Res.string.catalog_8_kk, Res.string.catalog_8_en),
    "сиропкарамель" to listOf(Res.string.catalog_9_ru, Res.string.catalog_9_kk, Res.string.catalog_9_en),
    "сиропсоленаякарамель" to listOf(Res.string.catalog_10_ru, Res.string.catalog_10_kk, Res.string.catalog_10_en),
    "сиропваниль" to listOf(Res.string.catalog_11_ru, Res.string.catalog_11_kk, Res.string.catalog_11_en),
    "безэссенции" to listOf(Res.string.catalog_12_ru, Res.string.catalog_12_kk, Res.string.catalog_12_en),
    "амареттоэссенция" to listOf(Res.string.catalog_13_ru, Res.string.catalog_13_kk, Res.string.catalog_13_en),
    "вишняэссенция" to listOf(Res.string.catalog_14_ru, Res.string.catalog_14_kk, Res.string.catalog_14_en),
    "грушаэссенция" to listOf(Res.string.catalog_15_ru, Res.string.catalog_15_kk, Res.string.catalog_15_en),
    "безмолока" to listOf(Res.string.catalog_16_ru, Res.string.catalog_16_kk, Res.string.catalog_16_en),
    "миндальноемолоко" to listOf(Res.string.catalog_17_ru, Res.string.catalog_17_kk, Res.string.catalog_17_en),
    "кокосовоемолоко" to listOf(Res.string.catalog_18_ru, Res.string.catalog_18_kk, Res.string.catalog_18_en),
    "фундучноемолоко" to listOf(Res.string.catalog_19_ru, Res.string.catalog_19_kk, Res.string.catalog_19_en),
    "флэтуайт" to listOf(Res.string.catalog_20_ru, Res.string.catalog_20_kk, Res.string.catalog_20_en),
    "раф" to listOf(Res.string.catalog_21_ru, Res.string.catalog_21_kk, Res.string.catalog_21_en),
    "hotcherry" to listOf(Res.string.catalog_22_ru, Res.string.catalog_22_kk, Res.string.catalog_22_en),
    "батчdaiquiri" to listOf(Res.string.catalog_23_ru, Res.string.catalog_23_kk, Res.string.catalog_23_en),
    "пряныйлатте" to listOf(Res.string.catalog_24_ru, Res.string.catalog_24_kk, Res.string.catalog_24_en),
    "рафhogwarts" to listOf(Res.string.catalog_25_ru, Res.string.catalog_25_kk, Res.string.catalog_25_en),
    "глинтвейн" to listOf(Res.string.catalog_26_ru, Res.string.catalog_26_kk, Res.string.catalog_26_en),
    "манго-имбирь" to listOf(Res.string.catalog_27_ru, Res.string.catalog_27_kk, Res.string.catalog_27_en),
    "смородина-грейпфрут" to listOf(Res.string.catalog_28_ru, Res.string.catalog_28_kk, Res.string.catalog_28_en),
    "зеленый-листовой" to listOf(Res.string.catalog_29_ru, Res.string.catalog_29_kk, Res.string.catalog_29_en),
    "бамблкофе" to listOf(Res.string.catalog_30_ru, Res.string.catalog_30_kk, Res.string.catalog_30_en),
    "эспрессотоник" to listOf(Res.string.catalog_31_ru, Res.string.catalog_31_kk, Res.string.catalog_31_en),
    "матчатоник" to listOf(Res.string.catalog_32_ru, Res.string.catalog_32_kk, Res.string.catalog_32_en),
    "апельсиновыйфреш" to listOf(Res.string.catalog_33_ru, Res.string.catalog_33_kk, Res.string.catalog_33_en),
    "классическийнасыщенныйчерныйкофемалогообъема.пробуждаетибодритспервогоглотка." to listOf(Res.string.catalog_34_ru, Res.string.catalog_34_kk, Res.string.catalog_34_en),
    "эспрессо,разбавленныйгорячейводой.идеальныйбаланскрепостиимягкостидлялюбителейчерногокофе." to listOf(Res.string.catalog_35_ru, Res.string.catalog_35_kk, Res.string.catalog_35_en),
    "самыйпопулярныйкофейныйнапитокнаосновеэспрессо,сдобавлениемтеплогомолокаигустой,плотноймолочнойпены." to listOf(Res.string.catalog_36_ru, Res.string.catalog_36_kk, Res.string.catalog_36_en),
    "мягкийкофейныйнапитоксбольшимколичествоммолокаитонкимслоемпенки.длятех,ктолюбитпонежнее." to listOf(Res.string.catalog_37_ru, Res.string.catalog_37_kk, Res.string.catalog_37_en),
    "насыщенныйкофейныйвкус.двойнойэспрессоитонкаямолочнаяпенка." to listOf(Res.string.catalog_38_ru, Res.string.catalog_38_kk, Res.string.catalog_38_en),
    "нежныйиневероятносливочныйкофейныйнапиток,взбитыйсванильнымсахаромисливками." to listOf(Res.string.catalog_39_ru, Res.string.catalog_39_kk, Res.string.catalog_39_en),
    "авторскийсогревающийнапитокснасыщеннымвишневымвкусомипряныминотками." to listOf(Res.string.catalog_40_ru, Res.string.catalog_40_kk, Res.string.catalog_40_en),
    "уникальныйкофейныйкоктейльсяркимхарактеромицитрусовымпослевкусием." to listOf(Res.string.catalog_41_ru, Res.string.catalog_41_kk, Res.string.catalog_41_en),
    "согревающийлаттесавторскиммиксомспеций.идеалендляпрохладныхдней." to listOf(Res.string.catalog_42_ru, Res.string.catalog_42_kk, Res.string.catalog_42_en),
    "магическийсливочныйнапитокссекретнымсиропом.вкус,которыйвозвращаетвдетство." to listOf(Res.string.catalog_43_ru, Res.string.catalog_43_kk, Res.string.catalog_43_en),
    "безалкогольныйгорячийнапитокнаосновесокасоспециями,фруктамиимедом." to listOf(Res.string.catalog_44_ru, Res.string.catalog_44_kk, Res.string.catalog_44_en),
    "бодрящийфруктовыйчайстропическиммангоисогревающимимбирем." to listOf(Res.string.catalog_45_ru, Res.string.catalog_45_kk, Res.string.catalog_45_en),
    "ягодныйбумслегкойцитрусовойгорчинкой.отличныйзарядвитаминов." to listOf(Res.string.catalog_46_ru, Res.string.catalog_46_kk, Res.string.catalog_46_en),
    "классическийзеленыйчайвысшегосорта.освежаетитонизирует." to listOf(Res.string.catalog_47_ru, Res.string.catalog_47_kk, Res.string.catalog_47_en),
    "освежающийслоистыйкоктейль:апельсиновыйфреш,карамельишотэспрессо." to listOf(Res.string.catalog_48_ru, Res.string.catalog_48_kk, Res.string.catalog_48_en),
    "холодныйкофейныйхит:бодрящийэспрессовсочетаниисшипучимтоникомильдом." to listOf(Res.string.catalog_49_ru, Res.string.catalog_49_kk, Res.string.catalog_49_en),
    "японскийзеленыйчайматчастоником.яркийцветинеобычныйсвежийвкус." to listOf(Res.string.catalog_50_ru, Res.string.catalog_50_kk, Res.string.catalog_50_en),
    "свежевыжатыйсокизотборныхапельсинов.100%польза." to listOf(Res.string.catalog_51_ru, Res.string.catalog_51_kk, Res.string.catalog_51_en),
)
@Composable fun catalogText(value: String, language: String): String {
    val resources = catalogTranslations[value.filterNot { it.isWhitespace() }.lowercase()] ?: return value.replace('\n', ' ')
    return stringResource(resources[when(language) { "kk" -> 1; "en" -> 2; else -> 0 }])
}

