(ns harja.kyselyt.paatos-kyselyt
  (:require [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [slingshot.slingshot :refer [throw+]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [harja.domain.valikatselmus :as valikatselmus-domain]
            [harja.kyselyt.urakat :as q-urakat]))

(defqueries "harja/kyselyt/paatos_kyselyt.sql"
  {:positional? true})

(declare tee-lupauspaatos<! poista-lupauspaatos<! hae-lupauspaatokset hae-lupauspaatos
  hae-tavoitehinnan-muutos-paatokset tee-tavoitehinnan-muutos-paatos<! hae-tavoitehinnan-muutospaatos poista-tavoitehinnan-muutos-paatos<!
  hae-tavoitehinnan-pysyvat-muutospaatokset tee-tavoitehinnan-pysyva-muutospaatos<! hae-tavoitehinnan-pysyva-muutospaatos poista-tavoitehinnan-pysyva-muutos-paatos<!
  tee-tavoitehinnan-alitus-paatos<! poista-tavoitehinnan-alitus-paatos<! hae-tavoitehinnnan-alitus-paatokset hae-tavoitehinnan-alituspaatos
  tee-tavoitehinnan-ylitys-paatos<! hae-tavoitehinnan-ylityspaatos hae-tavoitehinnnan-ylitys-paatokset poista-tavoitehinnan-ylitys-paatos<!
  hae-kattohinta-paatokset tee-kattohinta-paatos<! hae-kattohinta-paatos poista-kattohinta-paatos<!
  tee-hoitokauden-indeksikorjaus-paatos<! hae-hoitokauden-indeksikorjaus-paatos poista-hoitokauden-indeksikorjaus-paatos<! hae-hoitokauden-indeksikorjaus-paatokset
  tee-hoitokauden-lopun-hinta-paatos<! hae-hoitokauden-lopun-hintapaatos poista-hoitokauden-lopun-hinta-paatos<! hae-hoitokauden-lopun-hinta-paatokset
  tee-hoidonjohtopalkkio-paatos<! hae-hoidonjohtopalkkiopaatos poista-hoidonjohtopalkkio-paatos<! hae-hoidonjohtopalkkiopaatokset
  tee-poytakirjan-raporttipaatos<! hae-poytakirjan-raporttipaatos poista-poytakirjan-raporttipaatos<! hae-poytakirjan-raporttipaatokset
  hae-hoitokauden-lopun-indeksikorjaus
  hae-budjetoitu-hoidonjohtopalkkio-hoitokaudelle paivita-kattohinta<!)

(defn heita-virhe [viesti] (throw+ {:type "Error"
                                    :virheet {:koodi "ERROR" :viesti viesti}}))

(defn hae-indeksikorjauspaatokset [db parametrit]
  (let [vastaus (first (hae-hoitokauden-indeksikorjaus-paatokset db parametrit))
        vastaus (if vastaus
                 (let [kuukaudet-vector (konv/pgarray->vector (:hoitokauden_kuukaudet vastaus))
                       kuukaudet-map (mapv #(konv/pgobject->map % :kuukausi :string :indeksiluku :double) kuukaudet-vector)
                       vastaus (assoc vastaus :hoitokauden_kuukaudet kuukaudet-map)]
                   ;; Kysyjä olettaa saavansa vectorin
                   (conj [] vastaus))
                  vastaus)]
    vastaus))

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
        (= (:nimi paatos) "Hoitovuoden lopun indeksikorjaus") (hae-indeksikorjauspaatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Hoitovuoden lopun tavoite- ja kattohinta") (hae-hoitokauden-lopun-hinta-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Tavoitehinnan alitus") (hae-tavoitehinnnan-alitus-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Tavoitehinnan ylitys") (hae-tavoitehinnnan-ylitys-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Kattohinnan ylitys") (hae-kattohinta-paatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Hoidonjohtopalkkion muutos") (hae-hoidonjohtopalkkiopaatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})
        (= (:nimi paatos) "Välikatselmuspöytäkirjaan liitettävät raportit") (hae-poytakirjan-raporttipaatokset db {:urakkaid urakkaid :hoitokauden_alkuvuosi hoitokauden-alkuvuosi})))
    paatokset))

(defn tee-lupauspaatos
  "Lupauspäätöksen mäppi:
  {:hoitokauden_alkuvuosi <vuosi>
  :tyyppi <bonus|sakko|ei-bonus-ei-sakko>
  :urakkaid <urakka-id>
  :tavoitehinta <tavoitehinta>
  :tarjous_tavoitehinta <tavoitehinta>
  :luvatut_pisteet <pisteet>
  :toteutuneet_pisteet <pisteet>
  :lupausbonus <eurot>
  :lupaussanktio <eurot>
  :bonusprosentti <prosentti>
  :sanktioprosentti <prosentti>
  :indeksi <esim. MAKU 2015>
  :indeksikorotus <eurot>
  :erilliskustannus_id <luodun bonuksen id>
  :sanktio_id <luodun sanktion id>
  :luoja <kuka>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/lupauspaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/tavoitehinnan-muutospaatos paatos)))
        validaatio (if valid
                     validaatio
                     (conj validaatio "Puutteelliset lupauspäätöstiedot."))
        ;; Tarkista sakot
        validaatio (if (and (= "sanktio" (:tyyppi paatos)) (or (nil? (:lupaussanktio paatos)) (nil? (:sanktio_id paatos))))
                     (conj validaatio "Lupauspäätökseltä puuttuu sanktion määrä. ")
                     validaatio)
        ;; Tarkista bonus
        validaatio (if (and (= "bonus" (:tyyppi paatos)) (or (nil? (:lupausbonus paatos)) (nil? (:erilliskustannus_id paatos))))
                     (conj validaatio "Lupauspäätökseltä puuttuu bonuksen määrä.")
                     validaatio)]
    (if (seq validaatio)
      (do
        (log/error "Virheellinen lupauspäätös :: päätös:" (str (into (sorted-map) paatos)))
        (heita-virhe (str "Lupauspäätöksessä virheitä: " (string/join ", " validaatio))))
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
  {:hoitokauden_alkuvuosi <vuosi>
  :urakkaid <urakka-id>
  :muokkaa_kattohinta <versio>
  :tavoitehinta <tavoitehinta>
  :kattohinta <tavoitehinta>
  :luoja <kuka>}"
  [db {:keys [urakkaid] :as paatos} kayttajaid]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakkaid))
        urakan-alkuvuosi (-> urakan-tiedot :alkupvm (pvm/vuosi))
        hoitovuosinro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "1.10." (:hoitokauden_alkuvuosi paatos))))
        validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/tavoitehinnan-muutospaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/tavoitehinnan-muutospaatos paatos)))
        validaatio (if valid
                     validaatio
                     (conj validaatio "Puutteelliset tavoitehinnan muutospäätöstiedot."))]

    (if (seq validaatio)
      (do
        (log/error "Virheellinen tavoitehinnan muutospäätös:" paatos)
        (heita-virhe (str "Tavoitehinnan muutospäätöksessä virheitä: " (string/join ", " validaatio))))
      ;; Tallenna uudet tiedot tietokantaan
      (let [uusi-paatos (tee-tavoitehinnan-muutos-paatos<! db paatos)
            ;; Jos urakan alkuvuosi on 19/20, niin päivitetään myös kattohinta urakka_tavoite tauluun
            _ (when (or (= urakan-alkuvuosi 2019) (= urakan-alkuvuosi 2020))
                (paivita-kattohinta<! db {:urakkaid urakkaid
                                          :kattohinta (:kattohinta paatos)
                                          :hoitovuosinro hoitovuosinro
                                          :muokkaaja kayttajaid}))]
        uusi-paatos))))

(defn poista-tavoitehinnan-muutospaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että päätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
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
  :hoitokauden_alkuvuosi <vuosi>
  :hoitokauden_alun_tavoitehinta <eurot>
  :hoitokauden_lopun_tavoitehinta <eurot>
  :toteutuneet_kustannukset <eurot>
  :alituksen_maara <eurot>
  :siirron_maara <eurot>
  :tavoitepalkkio <eurot>
  :tavoitepalkkion_maksuprosentti <prosentti>
  :tavoitepalkkion_maksimi_prosentti <prosentti>
  :viimeinen_hoitokausi <boolean>
  :kulu_id <luodun kulun id>
  :luoja <kuka>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/tavoitehinnan-alituspaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/tavoitehinnan-alituspaatos paatos)))
        validaatio (if valid
                     validaatio
                     (conj validaatio "Puutteelliset tavoitehinnan alituspäätöstiedot."))]

    (if (seq validaatio)
      (do
        (log/error "Virheellinen päätös:" paatos)
        (heita-virhe (str "Tavoitehinnan alituspäätöksessä virheitä: " (string/join ", " validaatio))))
      (tee-tavoitehinnan-alitus-paatos<! db paatos))))

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
   :tavoitehinta <eurot>
   :toteutuneet_kustannukset <eurot>
   :ylityksen_maara <eurot>
   :tilaajan_prosentti <prosentti>
   :urakoitsijan_prosentti <prosentti>
   :tilaaja_maksaa <eurot>
   :urakoitsija_maksaa <eurot>
   :siirto <eurot> -- Siirto vielä epäselvä. Ei oteta vielä vastaan.
   :kulu_id <luodun kulun id>
   :viimeinen_hoitokausi <boolean>
   :luoja <kayttaja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/tavoitehinnan-ylityspaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/tavoitehinnan-ylityspaatos paatos)))
        validaatio (if valid validaatio (conj validaatio "Puutteelliset tavoitehinnan ylityspäätöstiedot."))

        ;; Tarkista siirto - TODO: Siirrosta puhutaan spekseissä, mutta ui:lla ei ole siihen toimintoa. Jätetään vielä toteuttamatta
        #_#_ validaatio (if (and (not (nil? (:siirto paatos))) (nil? (:kulu_id paatos)))
                     (conj validaatio "Tavoitehinnan ylityspäätöksen siirto- tai kulusummassa virhe. ")
                     validaatio)
        ;; Tarkista kulu
        #_#_ validaatio (if (and (not (nil? (:kulu_id paatos))) (nil? (:siirto paatos)))
                     (conj validaatio "Tavoitehinnan ylityspäätöksen siirto- tai kulusummassa virhe. ")
                     validaatio)
        ]

    (if (seq validaatio)
      (heita-virhe (str "Tavoitehinnan muutospäätöksessä virheitä: " (string/join ", " validaatio)))
      (tee-tavoitehinnan-ylitys-paatos<! db paatos))))

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
   :maksimi_siirrettava_maara <eurot>
   :siirtorajoitus_prosentti <prosentti>
   :kulu_id <luodun kulun id>
   :viimeinen_hoitokausi <boolean>
   :luoja <kayttaja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/kattohinnan-ylityspaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/kattohinnan-ylityspaatos paatos)))
        validaatio (if valid
                     validaatio
                     (conj validaatio "Puutteelliset kattohinnan ylityspäätöstiedot."))]
    (if (seq validaatio)
      (do
        (log/error "Puutteellinen kattohinnan ylityspäätös:" paatos)
        (heita-virhe (str "Kattohinnan muutospäätöksessä virheitä: " (string/join ", " validaatio))))
      (tee-kattohinta-paatos<! db paatos))))

(defn poista-kattohinnan-ylityspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että tavoitehinnan ylityspäätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-kattohinta-paatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Kattohinnan ylityspäätös ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))]
    (poista-kattohinta-paatos<! db {:poistaja kayttajaid :id paatosid})))

(defn tee-indeksikorjauspaatos
  "Indeksikorjauspäätöksen mäppi:
   {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :hv_alun_indkorj_tavoitehinta <eurot>
   :tavoitehinnan_muutokset <eurot>
   :hv_lopun_tavoitehinta_ennen_indkorj <eurot>
   :alkuperainen_pisteluku <pisteet>
   :alkuperaisen_pisteluvun_kuukausi <kuukausi vuosi>
   :pistelukujen_muutos <prosentti>
   :hoitokauden_kuukaudet <vektori kuukauden nimistä ja indeksiluvuista>
   :kuukausien_keskiarvo <pisteet>
   :indeksikorotuksen_prosenttiosuus <prosentti>
   :hoitokauden_lopun_indeksikorjaus <eurot>
   :luoja <luoja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/indeksikorjauspaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/indeksikorjauspaatos paatos)))
        validaatio (if valid
                     validaatio
                     (conj validaatio "Puutteelliset indeksikorjauspäätöstiedot."))
        ;; Tarkista hoitokauden kuukausien määrä
        validaatio (if (not= 12 (count (:hoitokauden_kuukaudet paatos)))
                     (conj validaatio "Indeksikorjauspäätöksen kuukausittaiset indeksiluvut puutteelliset. ")
                     validaatio)]

    (if (seq validaatio)
      (do
        (log/error "Virheellinen päätös:" paatos)
        (heita-virhe (str "Indeksikorjauspäätöksessä virheitä: " (string/join ", " validaatio))))
      (let [
            ;; Kuukausittaiset indeksit pitää konvertoida tietokantaa varten
            kuukaudet (map #(vals %) (:hoitokauden_kuukaudet paatos))
            hk (konv/seq->pg-object-literal kuukaudet)
            paatos (assoc paatos :hoitokauden_kuukaudet hk)
            vastaus (tee-hoitokauden-indeksikorjaus-paatos<! db paatos)
            konv-kuukaudet1 (konv/pgarray->vector (:hoitokauden_kuukaudet vastaus))
            hoitokauden-kuukaudet (map #(konv/pgobject->map % :kuukausi :string :indeksiluku :double) konv-kuukaudet1)
            hoitokauden-kuukaudet (mapv #(vals %) hoitokauden-kuukaudet)
            vastaus (assoc vastaus :hoitokauden_kuukaudet hoitokauden-kuukaudet)]
        vastaus))))

(defn poista-indeksikorjauspaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että indeksikorjauspäätös löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-hoitokauden-indeksikorjaus-paatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Hoidokauden lopun indeksikorjauspäätöstä ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))
        vastaus (poista-hoitokauden-indeksikorjaus-paatos<! db {:poistaja kayttajaid :id paatosid})]
    vastaus))

(defn tee-hoitokauden-lopun-hintapaatos
  "Hoitokauden lopun hintapäätöksen mäppi:
   {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :tavoitehinta_ennen <eurot>
   :tavoitehinta_jalkeen <eurot>
   :tavoitehinnan_muutokset <eurot>
   :hoitokauden_lopun_indeksikorjaus <eurot>
   :kattohinta <eurot>
   :luoja <luoja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (log/debug "tee-hoitokauden-lopun-hintapaatos :: paatos" (pr-str paatos))
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/hoitokauden-lopun-hintapaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/hoitokauden-lopun-hintapaatos paatos)))
        validaatio (if valid validaatio (conj validaatio "Puutteelliset indeksikorjauspäätöstiedot."))]

    (if (seq validaatio)
      (heita-virhe (str "Hoitokauden lopun hintapäätöksessä virheitä: " (string/join ", " validaatio)))
      (tee-hoitokauden-lopun-hinta-paatos<! db paatos))))

(defn poista-hoitovuoden-lopun-hintapaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että hoitokauden lopun hintapaatos löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-hoitokauden-lopun-hintapaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Hoidokauden lopun hintapäätöstä ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))
        vastaus (poista-hoitokauden-lopun-hinta-paatos<! db {:poistaja kayttajaid :id paatosid})]
    vastaus))

(defn tee-hoidonjohtopalkkiomuutospaatos
  "Hoidojohtopalkkion muutospäätöksen mäppi:
   {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :hv_lopun_indkorjaamaton_tavoitehinta <eurot>
   :tarjouksen_tavoitehinta <eurot>
   :hoidonjohtopalkkio <eurot>
   :muutosprosentti <prosentti>
   :hoidonjohtopalkkio_muutos <euro>
   :kulu_id <id>
   :luoja <luoja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/hoidonjohtopalkkiomuutospaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/hoidonjohtopalkkiomuutospaatos paatos)))
        validaatio (if valid validaatio (conj validaatio "Puutteelliset hoidonjohtopalkkionmuutospäätöstiedot."))]

    (if (seq validaatio)
      (heita-virhe (str "Hoitokauden lopun hintapäätöksessä virheitä: " (string/join ", " validaatio)))
      (tee-hoidonjohtopalkkio-paatos<! db paatos))))


(defn poista-hoidonjohtopalkkiomuutospaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että hoitokauden lopun hintapaatos löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-hoidonjohtopalkkiopaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Hoidonjohtopalkkionmuutospäätöstä ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))
        vastaus (poista-hoidonjohtopalkkio-paatos<! db {:poistaja kayttajaid :id paatosid})]
    vastaus))

(defn tee-poytakirjan-raporttipaatos
  "Välikatselmuksen pöytäkirjan raporttipäätöksen mäppi:
   {:urakkaid <urakkaid>
   :hoitokauden_alkuvuosi <hoitokauden-alkuvuosi>
   :luoja <luoja>}"
  [db paatos]
  ;; Varmistetaan, että tarvittavat tiedot on annettu
  (let [validaatio #{}
        ;; Validoi perustietojen pakollisuus
        valid (s/valid? ::valikatselmus-domain/raporttipaatos paatos)
        _ (when-not valid
            (log/error (s/explain-str ::valikatselmus-domain/raporttipaatos paatos)))
        validaatio (if valid validaatio (conj validaatio "Puutteelliset päätöstiedot."))]

    (if (seq validaatio)
      (do
        (log/error "Puutteellinen kattohinnan raporttipaatos:" paatos)
        (heita-virhe (str "Välikatselmuksen pöytäkirjan raporttipäätöksessä virheitä: " (string/join ", " validaatio))))
      (tee-poytakirjan-raporttipaatos<! db paatos))))

(defn poista-poytakirjan-raporttipaatos [db urakkaid kayttajaid paatosid]
  (let [;; Varmistetaan ensin, että raporttipaatos löytyy annetulla id:llä ja että se kuuluu annetulle urakalle
        paatos (first (hae-poytakirjan-raporttipaatos db {:paatos-id paatosid}))
        _ (when (or
                  (nil? paatos)
                  (not= urakkaid (:urakkaid paatos)))
            ;; Throw exception
            (throw (Exception. "Raporttipäätöstä ei löydy annetulla id:llä tai se ei kuulu annetulle urakalle")))
        vastaus (poista-poytakirjan-raporttipaatos<! db {:poistaja kayttajaid :id paatosid})]
    vastaus))
