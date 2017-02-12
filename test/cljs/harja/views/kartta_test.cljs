(ns harja.views.kartta-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [harja.views.kartta :as k])
  (:require-macros [harja.testutils.macros :refer [ei-virheita?]]))

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

(defn virheet [[_ validoinnin-tulos] vaatimukset alkuperainen]
  (let [epaonnistuneet (map first (remove second (dissoc validoinnin-tulos :kaikki-saannot-tarkastettu?)))
        virheviestit (keep
                       identity
                       (conj
                         []
                         (when-not (:kaikki-saannot-tarkastettu? validoinnin-tulos)
                           (str "Palautui ohjeita, joita testi ei tarkasta. Onko klikkauksesta-seuraavat-tapahtumat -funktiota muutettu? Odotettiin sääntöjä " (pr-str vaatimukset)))
                         (when (not-empty epaonnistuneet)
                           (str "Seuraavien ohjeiden ehdot eivät täyttyneet: " (clojure.string/join ", " (map name epaonnistuneet))))))]

    (if (not-empty virheviestit)
      (conj (str (pr-str virheviestit) " Alkuperäinen tulos oli " (pr-str alkuperainen)))
      [])))

(defn tarkista [{:keys [organisaatiot asiat tuplaklik? sivu ohjeet]
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
        (virheet ohjeet payload))))

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
      (is (nil? (ei-kasittelya {:sivu :ilmoitukset})))
      (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                :tuplaklik? true})))
      (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                :organisaatiot hy-valittu})))
      (is (nil? (ei-kasittelya {:sivu :ilmoitukset
                                :organisaatiot ur-valittu})))
      (is (empty? (tarkista {:sivu :ilmoitukset
                             :asiat [{:id 1}]
                             :organisaatiot ur-valittu
                             :ohjeet #{:keskeyta-event? :valitse-ilmoitus}})))
      (is (empty? (tarkista {:sivu :ilmoitukset
                             :asiat [{:id 1}]
                             :organisaatiot ur-valittu
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :valitse-ilmoitus :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :ilmoitukset
                             :asiat [{:id 1} {:id 2}]
                             :organisaatiot ur-valittu
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))
      (is (empty? (tarkista {:sivu :ilmoitukset
                             :asiat [{:id 1} {:id 2}]
                             :organisaatiot ur-valittu
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa
                                       :keskita-naihin}})))))

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
                                :tuplaklik? true})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :ohjeet #{:keskeyta-event? :avaa-paneeli?}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :organisaatiot yksi-urakka
                             :ohjeet #{:keskeyta-event? :avaa-paneeli?}})))

      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot yksi-urakka
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot yksi-urakka
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1} {:id 2}]
                             :organisaatiot yksi-urakka
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))

      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot yksi-urakka-joka-sattuu-olemaan-valittu
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot yksi-urakka-joka-sattuu-olemaan-valittu
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))

      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))

      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa-jossa-valittu-paalla
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa-jossa-valittu-paalla
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))

      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa-jossa-valittu-alla
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :tilannekuva
                             :asiat [{:id 1}]
                             :organisaatiot monta-urakkaa-jossa-valittu-alla
                             :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))))

  (let [hallintayksikon-valinta [{:type :hy :id (first +valitsemattomat-idt+)}
                                 {:type :hy :id (second +valitsemattomat-idt+)}]
        ohi-klikkaaminen-urakan-valinnassa [{:type :hy :id +valittu-id+}]
        urakan-valinta [{:type :ur :id (first +valitsemattomat-idt+)}
                        {:type :ur :id (second +valitsemattomat-idt+)}
                        {:type :hy :id +valittu-id+}]
        valittu-urakka [{:type :ur :id +valittu-id+}]]
    (testing "Klikkailua raporteissa"
      (is (empty? (tarkista {:sivu :raportit
                             :organisaatiot hallintayksikon-valinta
                             :ohjeet #{:keskeyta-event? :valitse-hallintayksikko}})))
      (is (empty? (tarkista {:sivu :raportit
                             :organisaatiot hallintayksikon-valinta
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :keskita-naihin :valitse-hallintayksikko}})))
      (is (empty? (tarkista {:sivu :raportit
                             :organisaatiot urakan-valinta
                             :ohjeet #{:keskeyta-event? :valitse-urakka}})))
      (is (empty? (tarkista {:sivu :raportit
                             :organisaatiot urakan-valinta
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :keskita-naihin :valitse-urakka}})))
      (is (nil? (ei-kasittelya {:sivu :raportit
                                :organisaatiot ohi-klikkaaminen-urakan-valinnassa})))
      (is (nil? (ei-kasittelya {:sivu :raportit
                                :organisaatiot valittu-urakka})))))

  (let [hallintayksikon-valinta [{:type :hy :id (first +valitsemattomat-idt+)}
                                 {:type :hy :id (second +valitsemattomat-idt+)}]
        ohi-klikkaaminen-urakan-valinnassa [{:type :hy :id +valittu-id+}]
        urakan-valinta [{:type :ur :id (first +valitsemattomat-idt+)}
                        {:type :ur :id (second +valitsemattomat-idt+)}
                        {:type :hy :id +valittu-id+}]
        valittu-urakka [{:type :ur :id +valittu-id+}]]
    (testing "Klikkailua etusivulla"
      (is (empty? (tarkista {:sivu :urakat
                             :organisaatiot hallintayksikon-valinta
                             :ohjeet #{:keskeyta-event? :valitse-hallintayksikko}})))
      (is (empty? (tarkista {:sivu :urakat
                             :organisaatiot hallintayksikon-valinta
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :valitse-hallintayksikko :keskita-naihin}})))
      (is (empty? (tarkista {:sivu :urakat
                             :organisaatiot urakan-valinta
                             :ohjeet #{:keskeyta-event? :valitse-urakka}})))
      (is (empty? (tarkista {:sivu :urakat
                             :organisaatiot urakan-valinta
                             :tuplaklik? true
                             :ohjeet #{:keskeyta-event? :valitse-urakka :keskita-naihin}})))
      (is (nil? (ei-kasittelya {:sivu :urakat
                                :organisaatiot ohi-klikkaaminen-urakan-valinnassa})))
      (is (nil? (ei-kasittelya {:sivu :urakat
                                :tuplaklik? true
                                :organisaatiot ohi-klikkaaminen-urakan-valinnassa}))))
    (testing "Klikkailua esim toteumissa"
      (let [urakan-klikkaaminen [{:type :ur :id +valittu-id+}]]
        (is (nil? (ei-kasittelya {:sivu :foobar
                                  :organisaatiot urakan-klikkaaminen
                                  :tuplaklik? true})))
        (is (empty? (tarkista {:sivu :foobar
                               :organisaatiot urakan-klikkaaminen
                               :ohjeet #{:keskeyta-event? :avaa-paneeli?}})))
        (is (empty? (tarkista {:sivu :foobar
                               :organisaatiot urakan-klikkaaminen
                               :asiat [{:id 1} {:id 2}]
                               :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa}})))
        (is (empty? (tarkista {:sivu :foobar
                               :organisaatiot urakan-klikkaaminen
                               :asiat [{:id 1} {:id 2}]
                               :tuplaklik? true
                               :ohjeet #{:keskeyta-event? :avaa-paneeli? :nayta-nama-paneelissa
                                         :keskita-naihin}})))))))

(deftest metatestit
  (testing "Toimiiko testifunktiot"
    (is (nil? (ei-kasittelya {:sivu :ilmoitukset})) "Nil tarkoittaa ei käsittelyä")
    (is (not (nil? (ei-kasittelya {:sivu :ilmoitukset
                                   :asiat [{:id 1}]})))
        "Vain nil tarkoittaa ei käsittelyä")
    (is (not (empty? (tarkista {:asiat [{:id 1}]
                                :sivu :ilmoitukset
                                :ohjeet #{:valitse-urakka}})))
        "Ylimääräiset ohjeet kippaa testin")
    (is (not (empty? (tarkista {:asiat [{:id 1}]
                                :sivu :ilmoitukset
                                :ohjeet #{:valitse-urakka :valitse-ilmoitus}})))
        "Ylimääräiset ohjeet kippaa testin, vaikka joukossa olisi oikeitakin")
    (is (not (empty? (tarkista {:asiat [{:id 1}]
                                :sivu :ilmoitukset
                                :ohjeet #{:valitse-ilmoitus}})))
        "Puuttuvat ohjeet kippaa testin")
    (is (not (empty? (tarkista {:asiat [{:id 1}]
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
