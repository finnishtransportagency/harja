(ns harja.palvelin.palvelut.muutos-materiaalitehtava-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.palvelin.palvelut.materiaalit :as materiaalit]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-tehtava-maaramuutokset (component/using
                                        (muutos-palvelu/->Muutos {:kehitysmoodi true})
                                        [:http-palvelin :db])
          :tallenna-suolatoteumat (component/using
                                    (materiaalit/->Materiaalit)
                                    [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)


(def ^{:private true} +urakka+ (hae-urakan-id-nimella "Iin MHU 2021-2026"))
(def ^{:private true} +sopimus+ (hae-iin-maanteiden-hoitourakan-2021-2026-sopimus-id))
(def ^{:private true} +hoitokaudet+ (mapv (fn [vuosi]
                                            [(pvm/hoitokauden-alkupvm vuosi)
                                             (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))]) (range 2021 2026)))


(defn- hae-maaramuutos-alkutiedot []
  (let [valittu-hoitokausi (nth +hoitokaudet+ 4)
        hae-maaramuutokset-fn #(kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-tehtava-maaramuutokset
                                 +kayttaja-jvh+
                                 %)

        maaramuutokset (hae-maaramuutokset-fn {:urakka-id +urakka+
                                               :hoitokaudet +hoitokaudet+
                                               :valittu-hoitokausi valittu-hoitokausi
                                               :laskenta-automatiikka? true})
        ;; Bäkkärissä lisätään gridiin väliotsikot 
        ;; otetaan ne pois, palautetaan raaka data 
        maaramuutokset-ei-valiotsikoita (filter #(not (:valiotsikko %)) maaramuutokset)]
    maaramuutokset-ei-valiotsikoita))


(defn- materiaalin-parametrit [materiaali]
  {:id (nth materiaali 0),
   :nimi (nth materiaali 1),
   :yksikko (nth materiaali 2),
   :kohdistettava (nth materiaali 3),
   :materiaalityyppi (nth materiaali 4),
   :urakkatyyppi (nth materiaali 5)
   :jarjestys (nth materiaali 6),
   :materiaaliluokka_id (nth materiaali 7),
   :yksiloiva_tunniste (nth materiaali 8)})


(deftest materiaalien-kohdistus-tehtaviin-materiaaliluokan-kautta
  (let [maaramuutokset (hae-maaramuutos-alkutiedot)
        suolaus (filter #(= (:tehtava %) "Liukkaudentorjunta suolaamalla (materiaali)") maaramuutokset)
        suolaus-toteumat-yhteensa (-> suolaus first :maara)

        ;; =========
        ;; Suolauksen alla ei ole vielä toteumia
        _ (is (= suolaus-toteumat-yhteensa 0.0M) "Liukkaudentorjunta suolaamalla (materiaali) toteumia ei ole")

        talvisuola-NaCl (first
                          (q (format "SELECT * FROM materiaalikoodi WHERE nimi = '%s';"
                               "Talvisuola, rakeinen NaCl")))
        talvisuolaliuos-CaCl2 (first
                                (q (format "SELECT * FROM materiaalikoodi WHERE nimi = '%s';"
                                     "Talvisuolaliuos CaCl2")))
        talvisuolaliuos-NaCl (first
                               (q (format "SELECT * FROM materiaalikoodi WHERE nimi = '%s';"
                                    "Talvisuolaliuos NaCl")))
        talvisuola-hiekoitus (first
                               (q (format "SELECT * FROM materiaalikoodi WHERE nimi = '%s';"
                                    "Hiekoitushiekan suola")))


        ;; =========
        ;; Tässä materiaalien toteuma määrät
        nacl-maara 0.123M
        cacl-maara 0.123M
        nacl-liuos-maara 0.123M
        hiekoitus-maara 0.123M

        suolaus-payload {:urakka-id +urakka+,
                         :sopimus-id +sopimus+,
                         :toteumat [{:rivinumero -1,
                                     :pvm (pvm/->pvm "1.10.2025"),
                                     :materiaali (materiaalin-parametrit talvisuola-NaCl)
                                     :maara nacl-maara,
                                     :lukumaara 1,
                                     :paattynyt nil}

                                    {:rivinumero -2,
                                     :pvm (pvm/->pvm "1.10.2025")
                                     :materiaali (materiaalin-parametrit talvisuolaliuos-CaCl2)
                                     :maara cacl-maara,
                                     :lukumaara 1,
                                     :paattynyt nil}

                                    {:rivinumero -3,
                                     :pvm (pvm/->pvm "1.10.2025")
                                     :materiaali (materiaalin-parametrit talvisuolaliuos-NaCl)
                                     :maara nacl-liuos-maara,
                                     :lukumaara 1,
                                     :paattynyt nil}

                                    {:rivinumero -4,
                                     :pvm (pvm/->pvm "1.10.2025")
                                     :materiaali (materiaalin-parametrit talvisuola-hiekoitus)
                                     :maara hiekoitus-maara,
                                     :lukumaara 1,
                                     :paattynyt nil}]}

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-suolatoteumat
                  +kayttaja-jvh+
                  suolaus-payload)

        ;; =========
        ;; Palauttaa true kun tallennus onnistui 
        _ (is (true? vastaus) "Suolan tallennus onnistui")

        maaramuutokset (hae-maaramuutos-alkutiedot)
        suolaus (filter #(= (:tehtava %) "Liukkaudentorjunta suolaamalla (materiaali)") maaramuutokset)
        suolaus-toteumat-yhteensa (-> suolaus first :maara)

        ;; =========
        ;; Suolauksen alla pitäisi lukea lisätyt suola materiaalit
        _ (is (= suolaus-toteumat-yhteensa
                (+ nacl-maara
                  cacl-maara
                  nacl-liuos-maara
                  hiekoitus-maara)) "Toteuma vastaa lisättyä payloadia")]))
