(ns harja.views.kartta-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [harja.views.kartta :as k]))

(def +valittu-id+ 1)
(def +valitsemattomat-idt+ (remove #(= % +valittu-id+) (drop 1 (range))))

(defn tarkista-saanto [avain & ehdot]
  (fn [[tulos edelliset] vaatimukset organisaatiot asiat]
    (if (vaatimukset avain)
      [(dissoc tulos avain)
       (assoc edelliset avain (every? #(% (avain tulos) organisaatiot asiat) ehdot))]
      [tulos edelliset])))

(def on-asia? #(and (some? %) (not (boolean? %)) (not (empty? %))))
(def listamainen? #(and (sequential? %) (not-empty %)))
(def keskeyta-event (tarkista-saanto
                      :keskeyta-event?
                      true?))
(def valitse-hallintayksikko (tarkista-saanto
                               :valitse-hallintayksikko
                               on-asia?
                               (fn [tulos orgs _]
                                 (and (= tulos (first orgs))
                                      (= :hy (:type (first orgs)))
                                      (not= +valittu-id+ (:id (first orgs)))))))
(def valitse-urakka (tarkista-saanto
                      :valitse-urakka
                      on-asia?
                      (fn [tulos orgs _]
                        (and (= tulos (first orgs))
                             (= :ur (:type (first orgs)))
                             (not= +valittu-id+ (:id (first orgs)))))))
(def valitse-ilmoitus (tarkista-saanto
                        :valitse-ilmoitus
                        on-asia?
                        (fn [tulos _ ilm]
                          (and (= tulos (first ilm))))))
(def avaa-paneeli? (tarkista-saanto
                     :avaa-paneeli?
                     true?))
(def nayta-nama-paneelissa (tarkista-saanto
                             :nayta-nama-paneelissa
                             listamainen?
                             (fn [tulos _ asiat]
                               (= tulos asiat))))
(def keskita-naihin (tarkista-saanto
                      :keskita-naihin
                      listamainen?
                      (fn [tulos org asiat]
                        (or (= tulos asiat)
                            (= tulos [(first org)])))))

(defn kaikki-saannot-tarkastettu? [[tulos edelliset]]
  [tulos (assoc edelliset :kaikki-saannot-tarkastettu? (empty? (keys tulos)))])

(defn validoi [tulos vaatimukset organisaatiot asiat]
  (-> [tulos {}]
      (keskeyta-event vaatimukset organisaatiot asiat)
      (valitse-hallintayksikko vaatimukset organisaatiot asiat)
      (valitse-urakka vaatimukset organisaatiot asiat)
      (valitse-ilmoitus vaatimukset organisaatiot asiat)
      (avaa-paneeli? vaatimukset organisaatiot asiat)
      (nayta-nama-paneelissa vaatimukset organisaatiot asiat)
      (keskita-naihin vaatimukset organisaatiot asiat)
      (kaikki-saannot-tarkastettu?)))

(defn virheviestit [[_ validoinnin-tulos] vaatimukset alkuperainen]
  (let [epaonnistuneet (map first (remove second (dissoc validoinnin-tulos :kaikki-saannot-tarkastettu?)))
        viestit (keep
                  identity
                  (conj
                    []
                    (when-not (:kaikki-saannot-tarkastettu? validoinnin-tulos)
                      (str "Palautui ohjeita, joita testi ei tarkasta. Onko klikkauksesta-seuraavat-tapahtumat -funktiota muutettu? Odotettiin sääntöjä " (pr-str vaatimukset)))
                    (when (not-empty epaonnistuneet)
                      (str "Seuraavien ohjeiden ehdot eivät täyttyneet: " (clojure.string/join ", " (map name epaonnistuneet))))))]

    (if (not-empty viestit)
      (conj (str (pr-str viestit) " Alkuperäinen tulos oli " (pr-str alkuperainen)))
      [])))

(defn virheet [{:keys [organisaatiot asiat tuplaklik? sivu ohjeet]
                :or {organisaatiot []
                     asiat []
                     tuplaklik? false}}]
  (assert (set? ohjeet) "Anna odotetut ohjeet setissä")
  (assert (every? #{:keskeyta-event? :valitse-hallintayksikko
                    :valitse-urakka :valitse-ilmoitus
                    :avaa-paneeli? :nayta-nama-paneelissa
                    :keskita-naihin} ohjeet) "Typo ohjeessa?")
  (assert (some? sivu) "Sivu on pakollinen tieto")
  (let [payload (k/klikkauksesta-seuraavat-tapahtumat (concat organisaatiot asiat)
                                                      tuplaklik?
                                                      sivu
                                                      +valittu-id+
                                                      +valittu-id+)]
    (-> payload
        (validoi ohjeet organisaatiot asiat)
        (virheviestit ohjeet payload))))

(defn ei-kasittelya [{:keys [organisaatiot asiat tuplaklik? sivu]
                      :or {organisaatiot []
                           asiat []
                           tuplaklik? false}}]
  (assert (some? sivu) "Sivu on pakollinen tieto")
  (k/klikkauksesta-seuraavat-tapahtumat (concat organisaatiot asiat)
                                        tuplaklik?
                                        sivu
                                        +valittu-id+
                                        +valittu-id+))

(deftest klikkauksesta-seuraava-tapahtumat
  (let [hy-valittu [{:type :hy :id +valittu-id+}]
        ur-valittu [{:type :ur :id +valittu-id+}]]
    (testing "Klikkailu ilmoitusnäkymässä"
      (let [v "Ilmoituksissa kaikki on piirretty frontilla - tyhjän pisteen klikkaaminen ei tee mitään"]
        (is (nil? (ei-kasittelya {:sivu :ilmoitukset})) v)
        (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                  :tuplaklik? true})) v)
        (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                  :organisaatiot hy-valittu})) v)
        (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                  :organisaatiot ur-valittu})) v)
        (is (empty? (virheet {:sivu :ilmoitukset
                              :asiat [{:id 1}]
                              :organisaatiot ur-valittu
                              :ohjeet #{:keskeyta-event? :valitse-ilmoitus}})) v))

      (let [v "Yhden ilmoituksen klikkaaminen avaa lomakkeen"]
        (is (empty? (virheet {:sivu :ilmoitukset
                              :asiat [{:id 1}]
                              :organisaatiot ur-valittu
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :valitse-ilmoitus :keskita-naihin}})) v)
        (is (empty? (virheet {:sivu :ilmoitukset
                              :asiat [{:id 1}]
                              :organisaatiot ur-valittu
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :valitse-ilmoitus :keskita-naihin}})) v))

      (let [v "Monen ilmoituksen klikkaaminen avaa paneelin"]
        (is (empty? (virheet {:sivu :ilmoitukset
                              :asiat [{:id 1} {:id 2}]
                              :organisaatiot ur-valittu
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)
        (is (empty? (virheet {:sivu :ilmoitukset
                              :asiat [{:id 1} {:id 2}]
                              :organisaatiot ur-valittu
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa
                                        :keskita-naihin}})) v))))

  (let [yksi-urakka [{:type :ur :id (first +valitsemattomat-idt+)}]
        yksi-urakka-joka-sattuu-olemaan-valittu [{:type :ur :id +valittu-id+}]
        ;; Klikkaus voi tilannekuvassa osua moneen urakkaan,
        ;; koska esim hoidon ja päällystyksen urakat voivat olla päällekkäin
        monta-urakkaa [{:type :ur :id (first +valitsemattomat-idt+)}
                       {:type :ur :id (second +valitsemattomat-idt+)}]
        monta-urakkaa-jossa-valittu-paalla [{:type :ur :id +valittu-id+}
                                            {:type :ur :id (first +valitsemattomat-idt+)}]
        monta-urakkaa-jossa-valittu-alla (reverse monta-urakkaa-jossa-valittu-paalla)]

    (testing "Klikkailua tilannekuvassa"
      (is (nil? (ei-kasittelya {:sivu :tilannekuva
                                :tuplaklik? true}))
          "Tuplaklik tyhjään pisteeseen aiheuttaa tilannekuvassa zoomin.")

      (let [v "Tyhjän pisteen klikkaaminen avaa infopaneelin"]
        (is (empty? (virheet {:sivu :tilannekuva
                              :ohjeet #{:keskeyta-event? :avaa-paneeli?}})) v)
        (is (empty? (virheet {:sivu :tilannekuva
                              :organisaatiot yksi-urakka
                              :ohjeet #{:keskeyta-event? :avaa-paneeli?}})) v))

      (let [v "Asian klikkaaminen avaa sen paneelissa"
            v_tpk (str v " ja tuplaklik keskittää siihen")]
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot yksi-urakka
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})) v_tpk)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot yksi-urakka
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1} {:id 2}]
                              :organisaatiot yksi-urakka
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)

        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot yksi-urakka-joka-sattuu-olemaan-valittu
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})) v_tpk)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot yksi-urakka-joka-sattuu-olemaan-valittu
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)

        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})) v_tpk)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)

        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa-jossa-valittu-paalla
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})) v_tpk)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa-jossa-valittu-paalla
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v)

        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa-jossa-valittu-alla
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})) v_tpk)
        (is (empty? (virheet {:sivu :tilannekuva
                              :asiat [{:id 1}]
                              :organisaatiot monta-urakkaa-jossa-valittu-alla
                              :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})) v))))

  (let [hallintayksikon-valinta [{:type :hy :id (first +valitsemattomat-idt+)}
                                 {:type :hy :id (second +valitsemattomat-idt+)}]
        ohi-klikkaaminen-urakan-valinnassa [{:type :hy :id +valittu-id+}]
        urakan-valinta [{:type :ur :id (first +valitsemattomat-idt+)}
                        {:type :ur :id (second +valitsemattomat-idt+)}
                        {:type :hy :id +valittu-id+}]
        valittu-urakka [{:type :ur :id +valittu-id+}]]
    (testing "Klikkailua raporteissa"
      (let [v "Raporteissa voi valita hallintayksikön kartalta"]
        (is (empty? (virheet {:sivu :raportit
                              :organisaatiot hallintayksikon-valinta
                              :ohjeet #{:keskeyta-event? :valitse-hallintayksikko}})) v)
        (is (empty? (virheet {:sivu :raportit
                              :organisaatiot hallintayksikon-valinta
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :keskita-naihin :valitse-hallintayksikko}})) v))

      (let [v "Raporteissa voi valita urakan kartalta"]
        (is (empty? (virheet {:sivu :raportit
                              :organisaatiot urakan-valinta
                              :ohjeet #{:keskeyta-event? :valitse-urakka}})) v)
        (is (empty? (virheet {:sivu :raportit
                              :organisaatiot urakan-valinta
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :keskita-naihin :valitse-urakka}})) v))

      (is (nil? (ei-kasittelya {:sivu :raportit
                                :organisaatiot ohi-klikkaaminen-urakan-valinnassa}))
          "'Ohi klikkaaminen' urakan valinnassa ei saa aiheuttaa toimenpiteitä")

      (is (nil? (ei-kasittelya {:sivu :raportit
                                :organisaatiot valittu-urakka}))
          "Jo valitun urakan klikkaaminen ei saa tarkoittaa mitään")))

  (let [hallintayksikon-valinta [{:type :hy :id (first +valitsemattomat-idt+)}
                                 {:type :hy :id (second +valitsemattomat-idt+)}]
        ohi-klikkaaminen-urakan-valinnassa [{:type :hy :id +valittu-id+}]
        urakan-valinta [{:type :ur :id (first +valitsemattomat-idt+)}
                        {:type :ur :id (second +valitsemattomat-idt+)}
                        {:type :hy :id +valittu-id+}]]
    (testing "Klikkailua etusivulla"
      (let [v "Hallintayksikön valinta etusivulla"]
        (is (empty? (virheet {:sivu :urakat
                              :organisaatiot hallintayksikon-valinta
                              :ohjeet #{:keskeyta-event? :valitse-hallintayksikko}})) v)
        (is (empty? (virheet {:sivu :urakat
                              :organisaatiot hallintayksikon-valinta
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :valitse-hallintayksikko :keskita-naihin}}))) v)
      (let [v "Urakan valinta etusivulla"]
        (is (empty? (virheet {:sivu :urakat
                             :organisaatiot urakan-valinta
                             :ohjeet #{:keskeyta-event? :valitse-urakka}})) v)
        (is (empty? (virheet {:sivu :urakat
                              :organisaatiot urakan-valinta
                              :tuplaklik? true
                              :ohjeet #{:keskeyta-event? :valitse-urakka :keskita-naihin}})) v))

      (let [v "Ohi klikkaaminen urakan valinnassa ei tee mitään"]
        (is (nil? (ei-kasittelya {:sivu :urakat
                                 :organisaatiot ohi-klikkaaminen-urakan-valinnassa})) v)
        (is (nil? (ei-kasittelya {:sivu :urakat
                                  :tuplaklik? true
                                  :organisaatiot ohi-klikkaaminen-urakan-valinnassa})) v)))

    (testing "Klikkailua esim toteumissa"
      (let [urakan-klikkaaminen [{:type :ur :id +valittu-id+}]]
        (is (nil? (ei-kasittelya {:sivu :foobar
                                  :organisaatiot urakan-klikkaaminen
                                  :tuplaklik? true}))
            "Tyhjän tuplaklikkaaminen zoomaa")

        (is (empty? (virheet {:sivu :foobar
                              :organisaatiot urakan-klikkaaminen
                              :ohjeet #{:keskeyta-event? :avaa-paneeli?}}))
            "Tyhjän klikkaaminen avaa paneelin")

        (let [v "Asioiden klikkaaminen avaa ne paneelissa"
              v_tpk (str v " ja keskittää niihin")]
          (is (empty? (virheet {:sivu :foobar
                                   :organisaatiot urakan-klikkaaminen
                                   :asiat [{:id 1} {:id 2}]
                                   :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}}))
              v)
          (is (empty? (virheet {:sivu :foobar
                                :organisaatiot urakan-klikkaaminen
                                :asiat [{:id 1}]
                                :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}}))
              v)

          (is (empty? (virheet {:sivu :foobar
                                :organisaatiot urakan-klikkaaminen
                                :asiat [{:id 1}]
                                :tuplaklik? true
                                :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa
                                          :keskita-naihin}}))
              v_tpk )
          (is (empty? (virheet {:sivu :foobar
                                :organisaatiot urakan-klikkaaminen
                                :asiat [{:id 1} {:id 2}]
                                :tuplaklik? true
                                :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa
                                          :keskita-naihin}}))
              v_tpk ))))))

(deftest metatestit
  (testing "Toimiiko testifunktiot"
    (is (nil? (ei-kasittelya {:sivu :ilmoitukset})) "Nil tarkoittaa ei käsittelyä")
    (is (not (nil? (ei-kasittelya {:sivu :ilmoitukset
                                   :asiat [{:id 1}]})))
        "Vain nil tarkoittaa ei käsittelyä")
    (is (not (empty? (virheet {:asiat [{:id 1}]
                               :sivu :ilmoitukset
                               :ohjeet #{:valitse-urakka}})))
        "Ylimääräiset ohjeet kippaa testin")
    (is (not (empty? (virheet {:asiat [{:id 1}]
                               :sivu :ilmoitukset
                               :ohjeet #{:valitse-urakka :valitse-ilmoitus}})))
        "Ylimääräiset ohjeet kippaa testin, vaikka joukossa olisi oikeitakin")
    (is (not (empty? (virheet {:asiat [{:id 1}]
                               :sivu :ilmoitukset
                               :ohjeet #{:valitse-ilmoitus}})))
        "Puuttuvat ohjeet kippaa testin")
    (is (not (empty? (virheet {:asiat [{:id 1}]
                               :sivu :ilmoitukset
                               :ohjeet #{}})))
        "Myös tyhjä ohjejoukko ymmärretään puuttuvina testeinä")

    (= (not= +valittu-id+ (take 1 +valitsemattomat-idt+)))
    (= (not= 0 (take 1 +valitsemattomat-idt+)))

    (is (on-asia? {:id 1}))
    (is (not (on-asia? {})))
    (is (not (on-asia? nil)))
    (is (not (on-asia? true)))
    (is (not (on-asia? false)))

    (is (listamainen? [{:id 1}]))
    (is (not (listamainen? [])))
    (is (not (listamainen? {})))
    (is (not (listamainen? true)))
    (is (not (listamainen? false)))
    (is (not (listamainen? nil))))

  (testing "Ohjeiden validointi toimii"
    (let [onnistui? #(and (empty? (first %)) (every? true? (vals (second %))))
          epaonnistui? #(and (empty? (first %)) (every? false? (vals (second %))))]

      (is (epaonnistui? [{} {:foobar false}]))
      (is (not (epaonnistui? [{} {:foobar true}])))
      (is (not (epaonnistui? [{:bar :baz} {:foobar false}])))

      (is (onnistui? [{} {:foobar true}]))
      (is (not (onnistui? [{} {:foobar false}])))
      (is (not (onnistui? [{:bar :baz} {:foobar true}])))

      (is (-> (valitse-hallintayksikko [{:valitse-hallintayksikko {:type :hy :id 3}} {}]
                                       #{:valitse-hallintayksikko}
                                       [{:type :hy :id 3} {:type :hy :id 2}]
                                       [])
              onnistui?))
      (is (-> (valitse-hallintayksikko [{:valitse-hallintayksikko {:id +valittu-id+ :type :hy}} {}]
                                       #{:valitse-hallintayksikko}
                                       [{:id +valittu-id+ :type :hy}]
                                       [])
              epaonnistui?))

      (is (-> (valitse-urakka [{:valitse-urakka {:type :ur :id 3}} {}]
                              #{:valitse-urakka}
                              [{:type :ur :id 3} {:type :ur :id 2}]
                              [])
              onnistui?))
      (is (-> (valitse-urakka [{:valitse-urakka {:id +valittu-id+ :type :ur}} {}]
                              #{:valitse-urakka}
                              [{:id +valittu-id+ :type :ur}]
                              [])
              epaonnistui?))

      (is (-> (valitse-ilmoitus [{:valitse-ilmoitus {:id 1}} {}]
                                #{:valitse-ilmoitus}
                                []
                                [{:id 1}])
              onnistui?))
      (is (-> (valitse-ilmoitus [{:valitse-ilmoitus {:id 1}} {}]
                                #{:valitse-ilmoitus}
                                []
                                [{:id 2} {:id 1}])
              epaonnistui?))

      (is (-> (nayta-nama-paneelissa [{:nayta-nama-paneelissa [{:id 1} {:id 2}]} {}]
                                     #{:nayta-nama-paneelissa}
                                     []
                                     [{:id 1} {:id 2}])
              onnistui?))
      (is (-> (nayta-nama-paneelissa [{:nayta-nama-paneelissa [{:id 1} {:id 2}]} {}]
                                     #{:nayta-nama-paneelissa}
                                     []
                                     [{:id 1}])
              epaonnistui?))

      (is (-> (keskita-naihin [{:keskita-naihin [{:id 1} {:id 2}]} {}]
                              #{:keskita-naihin}
                              []
                              [{:id 1} {:id 2}])
              onnistui?))
      (is (-> (keskita-naihin [{:keskita-naihin [{:id 1}]} {}]
                              #{:keskita-naihin}
                              []
                              [{:id 1} {:id 2}])
              epaonnistui?))
      (is (-> (keskita-naihin [{:keskita-naihin [{:id 1} {:id 2}]} {}]
                              #{:keskita-naihin}
                              [{:id 1} {:id 2}]
                              [])
              epaonnistui?))
      (is (-> (keskita-naihin [{:keskita-naihin [{:id 1}]} {}]
                              #{:keskita-naihin}
                              [{:id 1} {:id 2}]
                              [])
              onnistui?)))))
