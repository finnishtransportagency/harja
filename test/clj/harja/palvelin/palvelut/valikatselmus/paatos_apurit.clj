(ns harja.palvelin.palvelut.valikatselmus.paatos-apurit
  (:require [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]))

(defn lupauspaatos [urakkaid hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet lupausbonus
                    lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :tyyppi tyyppi
   :tavoitehinta tavoitehinta
   :tarjous_tavoitehinta tarjous-tavoitehinta
   :luvatut_pisteet luvatut-pisteet
   :toteutuneet_pisteet toteutuneet-pisteet
   :lupausbonus lupausbonus
   :lupaussanktio lupaussanktio
   :bonusprosentti bonusprosentti
   :sanktioprosentti sanktioprosentti
   :indeksi indeksi
   :indeksikorotus indeksikorotus
   :erilliskustannus_id erilliskustannus-id
   :sanktio_id sanktio-id
   :luoja luoja})

(defn tavoitehinnan-alituspaatos [urakkaid hoitokauden-alkuvuosi hoitokauden-alun-tavoitehinta hoitokauden-lopun-tavoitehinta toteutuneet-kustannukset
                                  alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti tavoitepalkkion_maksimi_prosentti kulu-id
                                  viimeinen_hoitokausi luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :hoitokauden_alun_tavoitehinta hoitokauden-alun-tavoitehinta
   :hoitokauden_lopun_tavoitehinta hoitokauden-lopun-tavoitehinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :alituksen_maara alituksen-maara
   :siirron_maara siirron-maara
   :tavoitepalkkio tavoitepalkkio
   :tavoitepalkkion_maksuprosentti tavoitepalkkion-maksuprosentti
   :tavoitepalkkion_maksimi_prosentti tavoitepalkkion_maksimi_prosentti
   :kulu_id kulu-id
   :viimeinen_hoitokausi viimeinen_hoitokausi
   :luoja luoja})

(defn tavoitehinnan-ylityspaatos [urakkaid hoitokauden-alkuvuosi tavoitehinta toteutuneet-kustannukset
                                  ylityksen-maara tilaajan-prosentti urakoitsijan-prosentti tilaaja-maksaa
                                  urakoitsija-maksaa siirto kulu-id viimeinen_hoitokausi luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :tavoitehinta tavoitehinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :ylityksen_maara ylityksen-maara
   :tilaajan_prosentti tilaajan-prosentti
   :urakoitsijan_prosentti urakoitsijan-prosentti
   :tilaaja_maksaa tilaaja-maksaa
   :urakoitsija_maksaa urakoitsija-maksaa
   :siirto siirto
   :kulu_id kulu-id
   :viimeinen_hoitokausi viimeinen_hoitokausi
   :luoja luoja})

(defn kattohinnan-ylityspaatos [urakkaid hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                                ylityksen-maara urakoitsija-maksaa siirrettava-maara kulu-id viimeinen_hoitokausi
                                maksimi-siirrettava-maara siirtorajoitus-prosentti luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :kattohinta kattohinta
   :toteutuneet_kustannukset toteutuneet-kustannukset
   :ylityksen_maara ylityksen-maara
   :urakoitsija_maksaa urakoitsija-maksaa
   :siirrettava_maara siirrettava-maara
   :kulu_id kulu-id
   :viimeinen_hoitokausi viimeinen_hoitokausi
   :maksimi_siirrettava_maara maksimi-siirrettava-maara
   :siirtorajoitus_prosentti siirtorajoitus-prosentti
   :luoja luoja})

(defn tavoitehinnan-muutospaatos [urakkaid hoitokauden-alkuvuosi muokkaa-kattohinta tavoitehinta kattohinta luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :muokkaa_kattohinta muokkaa-kattohinta
   :tavoitehinta tavoitehinta
   :kattohinta kattohinta
   :luoja luoja})

(defn indeksikorjauspaatos [urakkaid hoitokauden-alkuvuosi hv_alun_indkorj_tavoitehinta tavoitehinnan-muutokset hv_lopun_tavoitehinta_ennen_indkorj
                            hoitokauden-kuukaudet kuukausien-keskiarvo alkuperainen-pisteluku alkuperaisen-pisteluvun-kuukausi
                            pistelukujen-muutos pistelukujen-muutos-prosentteina indeksikorotuksen-prosenttiosuus
                            hoitokauden-lopun-indeksikorjaus luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :hv_alun_indkorj_tavoitehinta hv_alun_indkorj_tavoitehinta
   :tavoitehinnan_muutokset tavoitehinnan-muutokset
   :hv_lopun_tavoitehinta_ennen_indkorj hv_lopun_tavoitehinta_ennen_indkorj
   :alkuperainen_pisteluku alkuperainen-pisteluku
   :alkuperaisen_pisteluvun_kuukausi alkuperaisen-pisteluvun-kuukausi
   :pistelukujen_muutos pistelukujen-muutos
   :pistelukujen_muutos_prosentteina pistelukujen-muutos-prosentteina
   :hoitokauden_kuukaudet hoitokauden-kuukaudet
   :kuukausien_keskiarvo kuukausien-keskiarvo
   :indeksikorotuksen_prosenttiosuus indeksikorotuksen-prosenttiosuus
   :hoitokauden_lopun_indeksikorjaus hoitokauden-lopun-indeksikorjaus
   :luoja luoja})

(defn lopun-hintapaatos [urakkaid hoitokauden-alkuvuosi tavoitehinta_ennen hoitokauden-lopun-indeksikorjaus
                         tavoitehinnan_muutokset tavoitehinta_jalkeen kattohinta kattohintakerroin lisaa-tavoitehintaan-lopunindeksikorjaus kayttajaid]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :tavoitehinta_ennen tavoitehinta_ennen
   :hoitokauden_lopun_indeksikorjaus hoitokauden-lopun-indeksikorjaus
   :tavoitehinnan_muutokset tavoitehinnan_muutokset
   :tavoitehinta_jalkeen tavoitehinta_jalkeen
   :kattohinta kattohinta
   :kattohintakerroin kattohintakerroin
   :lisaa_tavoitehintaan_lopunindeksikorjaus lisaa-tavoitehintaan-lopunindeksikorjaus
   :luoja kayttajaid})

(defn hoidojohtopalkkiomuutospaatos [urakkaid hoitokauden-alkuvuosi tavoitehinta tarjouksen_tavoitehinta
                                     muutosprosentti hoidonjohtopalkkio hoidonjohtopalkkio_muutos kulu_id luoja]
  {:urakkaid urakkaid
   :hoitokauden_alkuvuosi hoitokauden-alkuvuosi
   :hv_lopun_indkorjaamaton_tavoitehinta tavoitehinta
   :tarjouksen_tavoitehinta tarjouksen_tavoitehinta
   :muutosprosentti muutosprosentti
   :hoidonjohtopalkkio hoidonjohtopalkkio
   :hoidonjohtopalkkio_muutos hoidonjohtopalkkio_muutos
   :kulu_id kulu_id
   :luoja luoja})

;; Lasketaan indeksikorotus lupaukselle, se pätee sekä bonukselle, että sanktiolle jos on päteäkseen
(defn laske-indeksikorotus-lupaukselle [db urakkaid paatos-pvm indeksi summa sanktio?]
  (let [indeksikorotus-parametrit {:pvm paatos-pvm
                                   :indeksi indeksi
                                   :maara summa
                                   :urakka-id urakkaid
                                   :sanktiolaji (if sanktio? "lupaussanktio" nil)}
        ;; Taustalla ajetaan tämmönen: SELECT korotus FROM sanktion_indeksikorotus(:pvm::DATE, :indeksi,:maara::NUMERIC, :urakka-id::INTEGER, :sanktiolaji::sanktiolaji);
        indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))]
    indeksikorotus))
