(ns harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat)

(def testidata
  [{:tr-kaista 1,
    :alikohteet [{:yllapitokohde 1,
                  :tr_kaista 1,
                  :karttapvm #inst"2016-07-02T17:25:03.000000000-00:00",
                  :tunnus nil,
                  :nimi "Laivaniemi 5",
                  :tr_loppuosa 1,
                  :tr_numero 18652,
                  :id 4,
                  :poistettu false,
                  :tr_loppuetaisyys 3312,
                  :tr_alkuetaisyys 5190,
                  :tr_ajorata 1,
                  :tr_alkuosa 1,
                  :toimenpide nil,
                  :yhaid nil}],
    :kohdenumero "L03",
    :tr-ajorata 1,
    :tr-loppuosa 1,
    :nykyinen-paallyste nil,
    :tr-alkuosa 1,
    :sopimuksen-mukaiset-tyot 400M,
    :tr-loppuetaisyys 3312,
    :nimi "Leppäjärven ramppi",
    :kaasuindeksi 0M,
    :bitumi-indeksi 4543.95M,
    :yllapitoluokka nil,
    :id 1,
    :sopimus 8,
    :tr-alkuetaisyys 5190,
    :tr-numero 18652,
    :arvonvahennykset 100M,
    :tyyppi "paallystys",
    :yhaid 1233534,
    :keskimaarainen-vuorokausiliikenne nil}
   {:tr-kaista 1,
    :alikohteet [{:yllapitokohde 2,
                  :tr_kaista 1,
                  :karttapvm #inst"2016-07-02T17:25:03.000000000-00:00",
                  :tunnus nil,
                  :nimi "Laivaniemi 4",
                  :tr_loppuosa 4,
                  :tr_numero 833,
                  :id 3,
                  :poistettu false,
                  :tr_loppuetaisyys 3042,
                  :tr_alkuetaisyys 334,
                  :tr_ajorata 1,
                  :tr_alkuosa 2,
                  :toimenpide nil,
                  :yhaid nil}],
    :kohdenumero "308",
    :tr-ajorata 1,
    :tr-loppuosa 4,
    :nykyinen-paallyste nil,
    :tr-alkuosa 2,
    :sopimuksen-mukaiset-tyot 9000M,
    :tr-loppuetaisyys 3042,
    :nimi "Tie 833",
    :kaasuindeksi 100M,
    :bitumi-indeksi 565M,
    :yllapitoluokka nil,
    :id 2,
    :sopimus 8,
    :tr-alkuetaisyys 334,
    :tr-numero 833,
    :arvonvahennykset 200M,
    :tyyppi "paallystys",
    :yhaid 54523243,
    :keskimaarainen-vuorokausiliikenne nil}
   {:tr-kaista 1,
    :alikohteet [{:yllapitokohde 3,
                  :tr_kaista 1,
                  :karttapvm #inst"2016-07-02T17:25:03.000000000-00:00",
                  :tunnus nil,
                  :nimi "Laivaniemi 3",
                  :tr_loppuosa 1,
                  :tr_numero 8484,
                  :id 2,
                  :poistettu false,
                  :tr_loppuetaisyys 5254,
                  :tr_alkuetaisyys 2728,
                  :tr_ajorata 1,
                  :tr_alkuosa 1,
                  :toimenpide nil,
                  :yhaid nil}],
    :kohdenumero "L010",
    :tr-ajorata 1,
    :tr-loppuosa 1,
    :nykyinen-paallyste nil,
    :tr-alkuosa 1,
    :sopimuksen-mukaiset-tyot 500M,
    :tr-loppuetaisyys 5254,
    :nimi "Nakkilan ramppi",
    :kaasuindeksi 6M,
    :bitumi-indeksi 5M,
    :yllapitoluokka nil,
    :id 3,
    :sopimus 8,
    :tr-alkuetaisyys 2728,
    :tr-numero 8484,
    :arvonvahennykset 3457M,
    :tyyppi "paallystys",
    :yhaid 265257,
    :keskimaarainen-vuorokausiliikenne nil}
   {:tr-kaista 1,
    :alikohteet [{:yllapitokohde 4,
                  :tr_kaista 1,
                  :karttapvm #inst"2016-07-02T17:25:03.000000000-00:00",
                  :tunnus nil,
                  :nimi "Laivaniemi 1",
                  :tr_loppuosa 10,
                  :tr_numero 19521,
                  :id 1,
                  :poistettu false,
                  :tr_loppuetaisyys 15,
                  :tr_alkuetaisyys 5,
                  :tr_ajorata 1,
                  :tr_alkuosa 10,
                  :toimenpide nil,
                  :yhaid nil}],
    :kohdenumero "310",
    :tr-ajorata 1,
    :tr-loppuosa 10,
    :nykyinen-paallyste nil,
    :tr-alkuosa 10,
    :sopimuksen-mukaiset-tyot 500M,
    :tr-loppuetaisyys 15,
    :nimi "Oulaisten ohitusramppi",
    :kaasuindeksi 6M,
    :bitumi-indeksi 5M,
    :yllapitoluokka nil,
    :id 4,
    :sopimus 8,
    :tr-alkuetaisyys 5,
    :tr-numero 19521,
    :arvonvahennykset 3457M,
    :tyyppi "paallystys",
    :yhaid 456896958,
    :keskimaarainen-vuorokausiliikenne nil}
   {:tr-kaista 1,
    :alikohteet [],
    :kohdenumero "666",
    :tr-ajorata 1,
    :tr-loppuosa 5,
    :nykyinen-paallyste nil,
    :tr-alkuosa 1,
    :sopimuksen-mukaiset-tyot 500M,
    :tr-loppuetaisyys 15,
    :nimi "Kuusamontien testi",
    :kaasuindeksi 6M,
    :bitumi-indeksi 5M,
    :yllapitoluokka nil,
    :id 5,
    :sopimus 8,
    :tr-alkuetaisyys 1,
    :tr-numero 20,
    :arvonvahennykset 3457M,
    :tyyppi "paallystys",
    :yhaid 456896959,
    :keskimaarainen-vuorokausiliikenne nil}])

(defn rakenna-sijainti [kohde]
  {:numero (or (:tr-numero kohde) (:tr_numero kohde))
   :aosa (or (:tr-alkuosa kohde) (:tr_alkuosa kohde))
   :aet (or (:tr-alkuetaisyys kohde) (:tr_alkuetaisyys kohde))
   :losa (or (:tr-loppuosa kohde) (:tr_loppuosa kohde))
   :let (or (:tr-loppuetaisyys kohde) (:tr_loppuetaisyys kohde))
   :ajr (or (:tr-ajorata kohde) (:tr_ajorata kohde))
   :kaista (or (:tr-kaista kohde) (:tr_kaista kohde))})

(defn rakenna-alikohde [alikohde]
  {:alikohde {:tunniste {:id (:id alikohde)}
              :tunnus (:tunnus alikohde)
              :nimi (:nimi alikohde)
              :sijainti (rakenna-sijainti alikohde)
              :toimenpide (:toimenpide alikohde)}})

(defn rakenna-kohde [kohde]
  {:tunniste {:id (:id kohde)}
   :sopimus {:id (:sopimus kohde)}
   :kohdenumero (:kohdenumero kohde)
   :nimi (:nimi kohde)
   :tyyppi (:tyyppi kohde)
   :sijainti (rakenna-sijainti kohde)
   :yllapitoluokka (:yllapitoluokka kohde)
   :keskimaarainen-vuorokausiliikenne (:keskimaarainen-vuorokausiliikenne kohde)
   :nykyinen-paallyste (:nykyinen-paallyste kohde)
   :alikohteet (mapv (fn [alikohde] (rakenna-alikohde alikohde)) (:alikohteet kohde))})

(defn rakenna-kohteet [yllapitokohteet]
  {:yllapitokohteet
   (mapv (fn [kohde] (hash-map :yllapitokohde (rakenna-kohde kohde)))
         yllapitokohteet)})