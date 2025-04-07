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
                                  alituksen-maara siirron-maara tavoitepalkkio tavoitepalkkion-maksuprosentti kulu-id
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
