(ns harja.ui.perus.rekisteri-test
  (:require [clojure.set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [harja.ui.perus.rekisteri :as rekisteri]))

(deftest rekisteri-on-ehja
  (is (empty? (rekisteri/virheet))))

(deftest virheet-tunnistaa-rikkinaisen-rekisterin
  (let [ok (first rekisteri/komponentit)]
    (testing "tupla-id"
      (is (seq (rekisteri/virheet rekisteri/ryhmat [ok ok]))))
    (testing "tuntematon ryhmä, luonne ja bootstrap-tila"
      (is (= 3 (count (rekisteri/virheet rekisteri/ryhmat
                        (cons (assoc ok :ryhma :ei-ole :luonne :ei-ole :bootstrap-tila :ei-ole)
                          (rest rekisteri/komponentit)))))))
    (testing "puuttuva kuvaus"
      (is (seq (rekisteri/virheet rekisteri/ryhmat [(dissoc ok :kuvaus)]))))))

(deftest virheet-tunnistaa-rakenteelliset-puutteet
  (let [ok (first rekisteri/komponentit)
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat (cons komponentti (rest rekisteri/komponentit))))]
    (testing "puuttuva rooli"
      (is (seq (virheet (dissoc ok :rooli)))))
    (testing "tuntematon rooli"
      (is (seq (virheet (assoc ok :rooli :ei-ole)))))
    (testing "puuttuvat ja tyhjät tilat ja sallitut vanhemmat"
      (doseq [avain [:tilat :sallitut-vanhemmat]]
        (is (seq (virheet (dissoc ok avain))) (str "puuttuva " avain))
        (is (seq (virheet (assoc ok avain #{}))) (str "tyhjä " avain))))
    (testing "slotit ja asetukset saavat olla tyhjiä, mutta eivät puuttua"
      (doseq [[avain tyhja] [[:slotit #{}] [:asetukset {}]]]
        (is (seq (virheet (dissoc ok avain))) (str "puuttuva " avain))
        (is (empty? (virheet (assoc ok avain tyhja))) (str "tyhjä " avain))))
    (testing "tuntematon vanhempi"
      (is (seq (virheet (assoc ok :sallitut-vanhemmat #{:ei-ole})))))
    (testing "variantit on erillinen avain eikä sekoitu tiloihin"
      (let [laatikko (rekisteri/hae :info-laatikko)]
        (is (contains? laatikko :variantit))
        (is (empty? (clojure.set/intersection (:tilat laatikko) (:variantit laatikko))))))))

(deftest ryhmittele-jarjestaa-ryhmat-ja-jattaa-tyhjat-pois
  (let [ryhmat [{:id :a :nimi "A"} {:id :tyhja :nimi "Tyhjä"} {:id :b :nimi "B"}]
        komponentit [{:id :x :ryhma :b} {:id :y :ryhma :a} {:id :z :ryhma :b}]]
    (is (= [[:a [:y]] [:b [:x :z]]]
           (map (fn [{:keys [id komponentit]}] [id (mapv :id komponentit)])
             (rekisteri/ryhmittele ryhmat komponentit))))))

(deftest hae-palauttaa-nil-tuntemattomalle-idlle
  (is (= :nappi (:id (rekisteri/hae :nappi))))
  (is (nil? (rekisteri/hae :ei-ole))))

(deftest ankkuri-johdetaan-id-sta
  (is (= "ui-komponenttien-tarkastelu-nappi" (rekisteri/ankkuri {:id :nappi}))))

(deftest sallitut-lapset-johdetaan-sallitusta-vanhemmista
  (doseq [{:keys [id sallitut-vanhemmat]} rekisteri/komponentit
          vanhempi sallitut-vanhemmat]
    (is (contains? (get (rekisteri/sallitut-lapset) vanhempi) id)
        (str id " on " vanhempi "n lapsi molempiin suuntiin"))))

(deftest api-teksti-generoituu-asetuksista
  (let [komponentti {:funktio "harja.ui.esimerkki/komponentti"
                     :asetukset {:teksti {:tyyppi :merkkijono :pakollinen? true :oletus nil :kuvaus "Teksti"}
                                 :disabled {:tyyppi :boolean :pakollinen? false :oletus false :kuvaus "Pois käytöstä"}}}]
    (is (= "harja.ui.esimerkki/komponentti: {:disabled :boolean, :teksti :merkkijono (pakollinen)}"
           (rekisteri/api-teksti komponentti)))))

(deftest nappi-kuvaa-asetuksensa
  (let [nappi (rekisteri/hae :nappi)]
    (is (contains? (:asetukset nappi) :data-cy))
    (is (= "harja.ui.napit/nappi" (:funktio nappi)))
    (is (str/starts-with? (rekisteri/api-teksti nappi) "harja.ui.napit/nappi: "))))

(deftest virheet-tunnistaa-suosituksen-puutteet
  (let [ok (first rekisteri/komponentit)
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat (cons komponentti (rest rekisteri/komponentit))))]
    (testing "tuntematon suositus"
      (is (seq (virheet (assoc ok :suositus :ei-ole)))))
    (testing "puuttuva suositus"
      (is (seq (virheet (dissoc ok :suositus)))))
    (testing "korvaa on oltava vektori"
      (is (seq (virheet (assoc ok :korvaa "yleiset/pudotusvalikko")))))))

(deftest legacy-paatos-on-yksiselitteinen
  (testing "uuteen UI:hin suositeltu komponentti on aktiivinen"
    (is (= :aktiivinen
           (rekisteri/legacy-paatos (rekisteri/hae :livi-pudotusvalikko)))))
  (testing "vanha suositus on legacy"
    (is (= :legacy
           (rekisteri/legacy-paatos (rekisteri/hae :rivi)))))
  (testing "eksplisiittinen päätös voi korjata suosituksen"
    (is (= :legacy
           (-> (rekisteri/hae :panel)
               (assoc :suositus :ei-suositusta)
               rekisteri/legacy-paatos))))
  (testing "päätöstä ei ole päätelty ei-suositusta-komponentille"
    (is (= :paatamatta
           (rekisteri/legacy-paatos (dissoc (rekisteri/hae :nappi)
                                      :legacy-paatos)))))
  (testing "tuntematon legacy-päätös hylätään"
    (is (some #(str/includes? % "tuntematon legacy-paatos")
          (rekisteri/virheet
            rekisteri/ryhmat
            (cons (assoc (first rekisteri/komponentit) :legacy-paatos :epaselva)
              (rest rekisteri/komponentit)))))))

(deftest uuden-merkinnan-nimeamiskaytanto-tarkistetaan
  (let [ok (assoc (first rekisteri/komponentit) :suositus :uuteen-ui)
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat (cons komponentti (rest rekisteri/komponentit))))
        disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Pois käytöstä."}]
    (testing "uudessa merkinnässä vanha nimi on virhe"
      (is (seq (virheet (assoc-in ok [:asetukset :disabled?] disabled?)))))
    (testing "vanhassa merkinnässä vanha nimi on sallittu"
      (is (empty? (virheet (assoc-in (assoc ok :suositus :vanha) [:asetukset :disabled?] disabled?)))))
    (testing "-fn-avaimen on oltava funktio"
      (is (seq (virheet (assoc-in ok [:asetukset :vaihda-fn]
                          {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Väärin."})))))
    (testing "?-avaimen on oltava boolean"
      (is (seq (virheet (assoc-in ok [:asetukset :tyhja?]
                          {:tyyppi :merkkijono :pakollinen? false :oletus nil :kuvaus "Väärin."})))))))

(deftest palautekomponentit-ovat-yhdessa-ryhmassa
  (is (= #{:palautteet}
         (set (map :ryhma (filter #(= :palaute (:rooli %)) rekisteri/komponentit))))))

(deftest nimeamispoikkeus-sallii-vain-kielletyn-asetuksen
  (let [disabled? {:tyyppi :boolean :pakollinen? false :oletus nil :kuvaus "Pois käytöstä."}
        livi (-> (rekisteri/hae :livi-pudotusvalikko)
                 (assoc-in [:asetukset :disabled?] disabled?)
                 (assoc :nimeamispoikkeukset #{:disabled?}))
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat
                    (map #(if (= (:id %) (:id komponentti)) komponentti %) rekisteri/komponentit)))]
    (is (empty? (virheet livi)))
    (testing "ilman poikkeusta uuden merkinnän :disabled? on virhe"
      (is (seq (virheet (dissoc livi :nimeamispoikkeukset)))))
    (testing "poikkeus asetukselle, jota ei ole, on virhe"
      (is (seq (virheet (assoc livi :nimeamispoikkeukset #{:ei-ole})))))
    (testing "poikkeus sallitulle asetusnimelle on virhe"
      (is (seq (virheet (assoc livi :nimeamispoikkeukset #{:disabled? :otsikko})))))))

(deftest palautekomponentin-variantit-vaativat-yhteiset-variantit
  (let [viesti (rekisteri/hae :viesti)
        virheet (fn [komponentti]
                  (rekisteri/virheet rekisteri/ryhmat
                    (map #(if (= (:id %) (:id komponentti)) komponentti %) rekisteri/komponentit)))]
    (is (empty? (virheet viesti)))
    (testing "puuttuva kartta"
      (is (seq (virheet (dissoc viesti :yhteiset-variantit)))))
    (testing "tuntematon yhteinen variantti"
      (is (seq (virheet (assoc-in viesti [:yhteiset-variantit :ei-ole] :success)))))
    (testing "kartan on oltava map"
      (is (seq (virheet (assoc viesti :yhteiset-variantit [:info])))))))

(deftest pudotusvalikon-suositus-on-yksiselitteinen
  (testing "livi on uuden UI:n ainoa suositeltu yksivalinta"
    (is (= :uuteen-ui (:suositus (rekisteri/hae :livi-pudotusvalikko))))
    (is (nil? (rekisteri/hae :valintaryhma)))
    (is (= #{"perus/valintaryhma" "kentat/tee-kentta :valinta"}
           (set (:korvaa (rekisteri/hae :livi-pudotusvalikko))))))
  (testing "komponentti ei korvaa itseään"
    (doseq [{:keys [funktio korvaa]} rekisteri/komponentit
            :let [lyhyt (str/replace funktio #"^harja\.ui\." "")
                  lyhyt (str/replace lyhyt "." "/")]]
      (is (not (some #{lyhyt} korvaa)) funktio))))

(deftest kortti-ja-tietolista-ovat-uuden-ui-n-korvaajat
  (let [kortti (rekisteri/hae :kortti)
        tietolista (rekisteri/hae :tietolista)]
    (is (= :uuteen-ui (:suositus kortti)))
    (is (= :uuteen-ui (:suositus tietolista)))
    (is (some #{"bootstrap/panel"} (:korvaa kortti)))
    (is (some #{"yleiset/tietoja"} (:korvaa tietolista)))
    (is (= :legacy (rekisteri/legacy-paatos (rekisteri/hae :panel))))
    (is (= :legacy (rekisteri/legacy-paatos (rekisteri/hae :tietoja))))))

(deftest kortti-sallii-panelin-tyypilliset-lapset
  (let [lapset (get (rekisteri/sallitut-lapset) :kortti)]
    (doseq [lapsi [:pino :vaaka :perusgrid :muokkausgrid :tietoja :tietolista :info-laatikko]]
      (is (contains? lapset lapsi) (str lapsi " ei ole sallittu kortin lapsi")))))
