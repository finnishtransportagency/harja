(ns harja.palvelin.palvelut.valikatselmus.paatos-apurit)

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
