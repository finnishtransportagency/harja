(ns harja.domain.valikatselmus
  "Urakan työtuntien skeemat."
  (:require [clojure.spec.alpha :as s]))

(s/def ::hoitokauden_alkuvuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::tyyppi string?)
(s/def ::muokkaa_kattohinta boolean?)
(s/def ::viimeinen_hoitokausi boolean?)
(s/def ::urakkaid int?)
(s/def ::tavoitehinta number?)
(s/def ::hv_alun_indkorj_tavoitehinta number?)
(s/def ::hv_lopun_tavoitehinta_ennen_indkorj number?)
(s/def ::tavoitehinta_ennen number?)
(s/def ::tavoitehinta_jalkeen number?)
(s/def ::tavoitehinnan_muutokset number?)
(s/def ::tavoitehinta_jalkeen number?)
(s/def ::hoitokauden_alun_tavoitehinta number?)
(s/def ::hoitokauden_lopun_tavoitehinta number?)
(s/def ::hv_lopun_indkorjaamaton_tavoitehinta number?)
(s/def ::kattohinta number?)
(s/def ::toteutuneet_kustannukset number?)
(s/def ::ylityksen_maara number?)
(s/def ::alituksen_maara number?)
(s/def ::urakoitsija_maksaa number?)
(s/def ::tarjous_tavoitehinta number?)
(s/def ::tarjouksen_tavoitehinta number?)
(s/def ::tavoitepalkkio number?)
(s/def ::siirrettava_maara number?)
(s/def ::siirto #(or (nil? %) (number? %)))
(s/def ::maksimi_siirrettava_maara number?)
(s/def ::siirron_maara #(or (nil? %) (number? %)))
(s/def ::siirtorajoitus_prosentti #(or (nil? %) (number? %)))
(s/def ::kirjalliset_muutokset #(or (nil? %) (number? %)))
(s/def ::rahavaraus_muutokset #(or (nil? %) (number? %)))
(s/def ::tehtava_muutokset #(or (nil? %) (number? %)))
(s/def ::tavoitepalkkion_maksuprosentti number?)
(s/def ::tavoitepalkkion_maksimi_prosentti number?)
(s/def ::luvatut_pisteet int?)
(s/def ::toteutuneet_pisteet int?)
(s/def ::luoja int?)
(s/def ::bonusprosentti number?)
(s/def ::kuukausien_keskiarvo number?)
(s/def ::alkuperaisen_pisteluvun_kuukausi string?)
(s/def ::alkuperainen_pisteluku number?)
(s/def ::pistelukujen_muutos number?)
(s/def ::pistelukujen_muutos_prosentteina number?)
(s/def ::indeksikorotuksen_prosenttiosuus number?)
(s/def ::hoitokauden_lopun_indeksikorjaus number?)
(s/def ::hoitokauden_kuukaudet #(not (nil? %)))
(s/def ::tilaajan_prosentti number?)
(s/def ::muutosprosentti number?)
(s/def ::urakoitsijan_prosentti number?)
(s/def ::tilaaja_maksaa number?)
(s/def ::hoidonjohtopalkkio number?)
(s/def ::hoidonjohtopalkkio_muutos number?)
(s/def ::kattohintakerroin number?)
(s/def ::tarkistettu #(inst? %))
(s/def ::lisaa_tavoitehintaan_lopunindeksikorjaus boolean?)
(s/def ::kirjallisesti_sovitut_muutokset #(or (nil? %) (number? %)))
(s/def ::pysyvat_muutokset #(or (nil? %) (number? %)))
(s/def ::johto_ja_hallintakorvaus_muutokset #(or (nil? %) (number? %)))
(s/def ::muutostyo_muutokset #(or (nil? %) (number? %)))
(s/def ::toteumiin_perustuvat_muutokset #(or (nil? %) (number? %)))
(s/def ::tehtava_ja_maaratoteumamuutokset #(or (nil? %) (number? %)))
(s/def ::rahavarausten_muutokset #(or (nil? %) (number? %)))
(s/def ::arvonvahennysten_muutokset #(or (nil? %) (number? %)))
(s/def ::tavoitehinnan_muutokset_yhteensa #(or (nil? %) (number? %)))

(s/def ::lupauspaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::tyyppi ::urakkaid ::tavoitehinta ::tarjous_tavoitehinta
                                        ::luvatut_pisteet ::toteutuneet_pisteet ::bonusprosentti ::sanktioprosentti ::luoja]
                            :opt-un [::lupausbonus ::lupaussanktio ::indeksi ::indeksikorotus ::erilliskustannus_id ::sanktio_id]))

(s/def ::tavoitehinnan-muutospaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::urakkaid ::muokkaa_kattohinta ::tavoitehinta
                                       ::kattohinta ::luoja]))

(s/def ::tavoitehinnan-pysyva-muutospaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::urakkaid ::kirjallisesti_sovitut_muutokset
                                                     ::pysyvat_muutokset ::johto_ja_hallintakorvaus_muutokset ::muutostyo_muutokset
                                                     ::toteumiin_perustuvat_muutokset ::tehtava_ja_maaratoteumamuutokset
                                                     ::rahavarausten_muutokset ::arvonvahennysten_muutokset ::tavoitehinnan_muutokset_yhteensa
                                                     ::luoja]))

(s/def ::kattohinnan-ylityspaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::urakkaid ::kattohinta ::toteutuneet_kustannukset
                                                   ::ylityksen_maara ::urakoitsija_maksaa ::siirrettava_maara ::maksimi_siirrettava_maara
                                                    ::viimeinen_hoitokausi ::luoja]
                                    :opt-un [::kulu_id ::siirtorajoitus_prosentti]))

(s/def ::tavoitehinnan-alituspaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::urakkaid ::hoitokauden_alun_tavoitehinta
                                                    ::hoitokauden_lopun_tavoitehinta ::toteutuneet_kustannukset ::alituksen_maara
                                                    ::siirron_maara ::tavoitepalkkio ::tavoitepalkkion_maksuprosentti ::tavoitepalkkion_maksimi_prosentti ::viimeinen_hoitokausi
                                                    ::luoja]
                                    :opt-un [::kulu_id]))

(s/def ::tavoitehinnan-ylityspaatos (s/keys :req-un [::hoitokauden_alkuvuosi ::urakkaid ::tavoitehinta
                                                     ::toteutuneet_kustannukset ::ylityksen_maara ::tilaajan_prosentti
                                                     ::urakoitsijan_prosentti ::tilaaja_maksaa ::urakoitsija_maksaa ::viimeinen_hoitokausi
                                                     ::luoja]
                                      :opt-un [::kulu_id ::siirto]))

(s/def ::indeksikorjauspaatos (s/keys :req-un [::urakkaid ::hoitokauden_alkuvuosi ::hv_alun_indkorj_tavoitehinta ::tavoitehinnan_muutokset ::hv_lopun_tavoitehinta_ennen_indkorj
                                               ::hoitokauden_kuukaudet ::kuukausien_keskiarvo ::alkuperaisen_pisteluvun_kuukausi
                                               ::alkuperainen_pisteluku ::pistelukujen_muutos ::pistelukujen_muutos_prosentteina  ::indeksikorotuksen_prosenttiosuus
                                               ::hoitokauden_lopun_indeksikorjaus ::luoja]))

(s/def ::hoitokauden-lopun-hintapaatos (s/keys :req-un [::urakkaid ::hoitokauden_alkuvuosi ::tavoitehinta_ennen ::tavoitehinta_jalkeen
                                                        ::tavoitehinnan_muutokset ::hoitokauden_lopun_indeksikorjaus ::kattohinta ::kattohintakerroin
                                                        ::lisaa_tavoitehintaan_lopunindeksikorjaus ::luoja]))

(s/def ::hoidonjohtopalkkiomuutospaatos (s/keys :req-un [::urakkaid ::hoitokauden_alkuvuosi ::hv_lopun_indkorjaamaton_tavoitehinta ::tarjouksen_tavoitehinta
                                                         ::hoidonjohtopalkkio ::muutosprosentti ::hoidonjohtopalkkio_muutos ::luoja]
                                          :opt-un [::kulu_id]))

(s/def ::raporttipaatos (s/keys :req-un [::urakkaid ::hoitokauden_alkuvuosi ::luoja]
                          :opt-un [::tarkistettu]))
