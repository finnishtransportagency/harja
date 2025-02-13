(ns harja.kyselyt.paatos-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [slingshot.slingshot :refer [throw+]]))

(defqueries "harja/kyselyt/paatos_kyselyt.sql"
  {:positional? true})

(declare tee-lupauspaatos<! poista-lupauspaatos<! hae-lupauspaatokset hae-lupauspaatos
  tee-tavoitehinnan-muutos-paatos<! hae-tavoitehinnan-muutospaatos poista-tavoitehinnan-muutos-paatos<!
  tee-tavoitehinnan-alitus-paatos<! poista-tavoitehinnan-alitus-paatos<! hae-tavoitehinnnan-alitus-paatokset hae-tavoitehinnan-alituspaatos
  tee-tavoitehinnan-ylitys-paatos<! hae-tavoitehinnan-ylityspaatos hae-tavoitehinnnan-ylitys-paatokset poista-tavoitehinnan-ylitys-paatos<!
  tee-kattohinta-paatos<! hae-kattohinta-paatos poista-kattohinta-paatos<!)

(defn heita-virhe [viesti] (throw+ {:type "Error"
                                    :virheet {:koodi "ERROR" :viesti viesti}}))

;; Haetaan päätöskoneen listauksen mukaiset päätökset tietokannasta
(defn hae-paatokset
  "Anna listaus mäppejä, jonka päätöskone on luonut
  Esim: [{:nimi \"Lupaukset\" :tyyppi \"bonus\" :urakan_alkuvuosi 2019 :nakyvyys_alkaen 2019 :hoitotyyppi #{\"MHU\"} :jarjestys 1}]"
  [db paatokset urakkaid hoitokauden-alkuvuosi]
  (mapcat
    (fn [paatos]
      (cond
        (= (:nimi paatos) "Lupaukset") (hae-lupauspaatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Tavoitehinnan muutokset") (hae-tavoitehinnan-muutos-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Hoitovuoden lopun indeksikorjaus") (hae-hoitokauden-indeksikorjaus-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Hoitovuoden lopun tavoite- ja kattohinta") (hae-hoitokauden-lopun-hinta-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Tavoitehinnan alitus") (hae-tavoitehinnnan-alitus-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Tavoitehinnan ylitys") (hae-tavoitehinnnan-ylitys-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Kattohinnan ylitys") (hae-kattohinta-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Hoidonjohtopalkkion muutos") (hae-hoidonjohtopalkkiopaatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        ;;TODO: Tätä ei ole vielä määritelty tarpeeksi.
        (= (:nimi paatos) "Välikatselmuspöytäkirjaan liitettävät raportit") nil))
    paatokset))

(defn tee-lupauspaatos
  "Lupauspäätöksen mäppi:
  {:hoitovuoden_alkuvuosi <vuosi>
  :tyyppi <bonus|sakko|ei-bonus-ei-sakko>
  :urakkaid <urakka-id>
  :tavoitehinta <tavoitehinta>
  :tarjous_tavoitehinta <tavoitehinta>
  :luvatut_pisteet <pisteet>
  :toteutuneet_pisteet <pisteet>
  :lupausbonus <eurot>
  :lupaussanktio <eurot>
  :erilliskustannus_id <luodun bonuksen id>
  :sanktio_id <luodun sanktion id>
  :luoja <kuka>}"
  [db urakkaid paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        validaatio (if (and (:hoitovuoden_alkuvuosi paatos) (:tyyppi paatos) (:urakkaid paatos) (:tavoitehinta paatos)
                            (:tarjous_tavoitehinta paatos) (:luvatut_pisteet paatos) (:toteutuneet_pisteet paatos)
                            (:luoja paatos))
                     (conj validaatio "Puutteelliset lupauspäätöstiedot.")
                     validaatio)
        ;; Tarkista sakot
        validaatio (if (and (= "sanktio" (:tyyppi paatos)) (or (nil? (:lupaussanktio paatos)) (nil? (:sanktio_id paatos))))
                     (conj validaatio "Lupauspäätökseltä puuttuu sanktion määrä. ")
                     validaatio)
        ;; Tarkista bonus
        validaatio (if (and (= "bonus" (:tyyppi paatos)) (or (nil? (:lupausbonus paatos)) (nil? (:erilliskustannus_id paatos))))
                     (conj validaatio "Lupauspäätökseltä puuttuu bonuksen määrä.")
                     validaatio)]

    (if (seq validaatio)
      (heita-virhe (str "Lupauspäätöksessä virheitäs: " (clojure.string/join ", " validaatio)))
      (tee-lupauspaatos<! db paatos))))

(defn poista-lupauspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että lupaus löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        lupauspaatos (first (hae-lupauspaatos db {:paatos-id paatosid}))
        _ (when (or
                (nil? lupauspaatos)
                (not= urakkaid (:urakkaid lupauspaatos)))
            ;; Throw exception
            (throw (Exception. "Lupauspäätös ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))]
    (poista-lupauspaatos<! db {:poistaja kayttajaid :id paatosid})))

(defn tee-tavoitehinnan-muutospaatos
  "Tavoitehinnan muutospäätös mäppi:
  {:hoitovuoden_alkuvuosi <vuosi>
  :urakkaid <urakka-id>
  :versio <versio>
  :tavoitehinta <tavoitehinta>
  :kattohinta <tavoitehinta>
  :luoja <kuka>}"
  [db urakkaid paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  ;;TODO: Tee validaatio
  (tee-tavoitehinnan-muutos-paatos<! db paatos))

(defn poista-tavoitehinnan-muutospaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että lupaus löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-tavoitehinnan-muutospaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Tavoitehinnan muutospäätöstä ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle.")))]
    (poista-tavoitehinnan-muutos-paatos<! db {:poistaja kayttajaid :id paatosid})))

(defn tee-tavoitehinnan-alituspaatos
  "Tavoitehinnan alityspäätöksen mäppi:
  {:urakkaid <urakka-id>
  :hoitovuoden_alkuvuosi <vuosi>
  :versio <versio>
  :tavoitehinta <tavoitehinta>
  :toteutuneet_kustannukset <eurot>
  :alituksen_maara <eurot>
  :siirron_maara <eurot>
  :tavoitepalkkio <eurot>
  :kulu_id <luodun kulun id>
  :luoja <kuka>}"
  [db urakkaid paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  ;;TODO: Tee validaatio
  (tee-tavoitehinnan-alitus-paatos<! db paatos))

(defn poista-tavoitehinnan-alituspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että tavoitehinnan alityspäätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-tavoitehinnan-alituspaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Tavoitehinnan alituspäätös ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))]
    (poista-tavoitehinnan-alitus-paatos<! db {:poistaja kayttajaid :id paatosid})))

(defn tee-tavoitehinnan-ylityspaatos
  "Tavoitehinnan ylityspäätöksen mäppi:
  {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :versio <versio>
   :tavoitehinta <eurot>
   :toteutuneet_kustannukset <eurot>
   :ylityksen_maara <eurot>
   :tilaajan_prosentti <prosentti>
   :urakoitsijan_prosentti <prosentti>
   :tilaaja_maksaa <eurot>
   :urakoitsija_maksaa <eurot>
   :siirto <eurot>
   :kulu_id <luodun kulun id>
   :luoja <kayttaja>}"
  [db urakkaid paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  ;;TODO: Tee validaatio
  (tee-tavoitehinnan-ylitys-paatos<! db paatos))

(defn poista-tavoitehinnan-ylityspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että tavoitehinnan ylityspäätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-tavoitehinnan-ylityspaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Tavoitehinnan ylityspäätös ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))]
    (poista-tavoitehinnan-ylitys-paatos<! db {:poistaja kayttajaid :id paatosid})))

(defn tee-kattohinnan-ylityspaatos
  "Kattohinnan ylityspäätöksen mäppi:
  {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :kattohinta <eurot>
   :toteutuneet_kustannukset <eurot>
   :ylityksen_maara <eurot>
   :urakoitsija_maksaa <eurot>
   :siirrettava_maara <eurot>
   :kulu_id <luodun kulun id>
   :luoja <kayttaja>}"
  [db urakkaid paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  ;;TODO: Tee validaatio
  (tee-kattohinta-paatos<! db paatos))

(defn poista-kattohinnan-ylityspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että tavoitehinnan ylityspäätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-kattohinta-paatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Kattohinnan ylityspäätös ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))]
    (poista-kattohinta-paatos<! db {:poistaja kayttajaid :id paatosid})))
