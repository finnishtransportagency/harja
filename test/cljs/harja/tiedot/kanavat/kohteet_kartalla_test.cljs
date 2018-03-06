(ns harja.tiedot.kanavat.kohteet-kartalla-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [harja.tiedot.kanavat.kohteet-kartalla :as kohteet-kartalla]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as kokonaishintaiset]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.urakka :as urakka]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.pvm :as pvm]
            [harja.testutils :as testutils]))

;;;;;;;;;
;; Speckejä kohteet-kartalla ns:n käyttämille reactioneille

(s/def ::type #{:point})
(s/def ::coordinates (s/coll-of number? :kind vector? :count 2))
(s/def ::sijainti (s/keys :req-un [::type ::coordinates]))
(s/def ::kohde/sijainti ::sijainti)
(s/def ::osa/sijainti ::sijainti)
(s/def ::kanavan-toimenpide/sijainti ::sijainti)

(s/def ::kohde/urakat (s/keys :req [::urakka/nimi ::urakka/id]))
(s/def ::nakymassa? true?)

(defn generaattori-setista-speceja [specit]
  (let [specit-vektorina (into [] specit)]
     (gen/fmap (fn [generoidut]
                 (zipmap specit-vektorina generoidut))
               (apply gen/tuple (map (fn [perustieto]
                                       (s/gen perustieto))
                                     specit-vektorina)))))

;; ----- Kokonaishintaiset
(s/def ::toimenpide (s/with-gen map?
                                #(let [;; Toimenpiteessä käytetään @kanavaurakka/kanavakohteet arvoja,
                                       ;; joten ::kanavan-toimenpide/kohde avaimen arvoa ei voi jättää
                                       ;; aivan satunnaisuuden varaan.
                                       ;;
                                       ;; Vähän härö antaa yhdessä tapauksessa funktio ja toisessa setti,
                                       ;; mutta näin saadaan tarvittaessa generoitua nil
                                       kanavakohteet (if (empty? @kanavaurakka/kanavakohteet)
                                                       nil?
                                                       (into #{} @kanavaurakka/kanavakohteet))]
                                   (gen/fmap (fn [[kohde toimenpide huoltokohde kayttaja toimenpidekoodi]]
                                               (let [toimenpide (into {}
                                                                      (map (fn [[avain arvo]]
                                                                             (if (instance? js/Date arvo)
                                                                               [avain (doto
                                                                                        (js/goog.date.DateTime.)
                                                                                        (.setTime (.getTime arvo)))]
                                                                               [avain arvo]))
                                                                           toimenpide))]
                                                 (merge toimenpide
                                                        (when kohde
                                                          {::kanavan-toimenpide/kohde kohde
                                                           ::kanavan-toimenpide/huoltokohde huoltokohde
                                                           ::kanavan-toimenpide/kohteenosa (-> kohde ::kohde/kohteenosat first)
                                                           ::kanavan-toimenpide/kuittaaja kayttaja
                                                           ::kanavan-toimenpide/toimenpidekoodi toimenpidekoodi}))))
                                             (gen/tuple (s/gen kanavakohteet)
                                                        (s/gen ::kanavan-toimenpide/kanava-toimenpide)
                                                        (generaattori-setista-speceja huoltokohde/perustiedot)
                                                        (generaattori-setista-speceja kayttaja/perustiedot)
                                                        (generaattori-setista-speceja toimenpidekoodi/perustiedot))))))
(s/def ::toimenpiteet (s/coll-of ::toimenpide :min-count 1))

(s/def ::kokonaishintaiset-grid-nakyma (s/keys :req-un [::toimenpiteet ::nakymassa?]))

;; ------ Kanavakohteet
(s/def ::kohde/kohteenosa (s/with-gen #(map? %)
                                      #(generaattori-setista-speceja osa/perustiedot)))
(s/def ::kohde/kohteenosat (s/coll-of ::kohde/kohteenosa :min-count 1 :max-count 5))

(s/def ::kanavakohde (s/with-gen (s/keys :req [::kohde/kohteenosat ::kohde/id ::kohde/sijainti ::kohde/nimi ::kohde/urakat])
                                 ;; Tällä generaattorilla pyritään saamaan eri id arvot kaikille kohteille
                                 #(let [idt (atom #{})]
                                    (gen/fmap (fn [[kohteenosat id sijainti nimi urakat]]
                                                (swap! idt conj id)
                                                {::kohde/kohteenosat kohteenosat
                                                 ::kohde/id id
                                                 ::kohde/sijainti sijainti
                                                 ::kohde/nimi nimi
                                                 ::kohde/urakat urakat})
                                              (gen/tuple (s/gen ::kohde/kohteenosat)
                                                         (gen/such-that (fn [generoitu-id]
                                                                          (not (@idt generoitu-id)))
                                                                        (s/gen ::kohde/id))
                                                         (s/gen ::kohde/sijainti)
                                                         (s/gen ::kohde/nimi)
                                                         (s/gen ::kohde/urakat))))))
(s/def ::kanavakohteet (s/coll-of ::kanavakohde :min-count 1))

;; Speckien loppu
;;;;;;;;;;


(deftest nayta-kohteet-kartalla
  (let [;; Kanavakohteet generoidaan ensin ja asetaetaan paikalleen,
        ;; koska sitä käytetään generoitaessa kokonaishintaisen tilaa
        kanavakohteet-tila (gen/generate (s/gen ::kanavakohteet))
        _ (reset! kanavaurakka/kanavakohteet kanavakohteet-tila)
        kokonaishintaiset-tila (gen/generate (s/gen ::kokonaishintaiset-grid-nakyma))]
    ;; Näytetään kartta
    (reset! kohteet-kartalla/karttataso-kohteet true)
    ;; Laitetaan kokonaishintasten tilaksi joku generoitu arvo
    (reset! kokonaishintaiset/tila kokonaishintaiset-tila)
    (is (= (count (-> @kohteet-kartalla/aktiivinen-nakyma :tila :gridissa-olevat-kohteen-tiedot))
           (count @kohteet-kartalla/naytettavat-kanavakohteet))
        "Kartalla pitäisi olla yhtä monta näytettävää kohdetta, kuin mitä toimenpiteitä on tehty")
    (doseq [kohde (-> @kohteet-kartalla/aktiivinen-nakyma :tila :gridissa-olevat-kohteen-tiedot)
            :let [kohde-kartalla (some (fn [kohde-kartalla]
                                         (when (and (= (:nimi kohde-kartalla)
                                                       (-> kohde ::kanavan-toimenpide/kohde ::kohde/nimi))
                                                    (= (-> kohde-kartalla :toimenpiteet :kuittaaja)
                                                       (str (-> kohde ::kanavan-toimenpide/kuittaaja ::kayttaja/etunimi) " "
                                                            (-> kohde ::kanavan-toimenpide/kuittaaja ::kayttaja/sukunimi))))
                                           kohde-kartalla))
                                       @kohteet-kartalla/kohteet-kartalla)]]
      (is (not (nil? kohde-kartalla)))
      (is (= (:sijainti kohde-kartalla)
             (-> kohde ::kanavan-toimenpide/kohde ::kohde/sijainti)))
      (is (= (:toimenpiteet kohde-kartalla)
             {:huoltokohde (-> kohde ::kanavan-toimenpide/huoltokohde ::huoltokohde/nimi)
              :kohteenosan-tyyppi (when-let [tyyppi (-> kohde ::kanavan-toimenpide/kohteenosa ::osa/tyyppi)]
                                    (kohteet-kartalla/tekstiksi tyyppi))
              :kuittaaja (str (-> kohde ::kanavan-toimenpide/kuittaaja ::kayttaja/etunimi) " "
                              (-> kohde ::kanavan-toimenpide/kuittaaja ::kayttaja/sukunimi))
              :lisatieto (::kanavan-toimenpide/lisatieto kohde)
              :muu-toimenpide (::kanavan-toimenpide/muu-toimenpide kohde)
              :pvm (when-let [paivamaara (::kanavan-toimenpide/pvm kohde)] (pvm/pvm paivamaara))
              :suorittaja (::kanavan-toimenpide/suorittaja kohde)
              :toimenpide (-> kohde ::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi)})))
    ;; Avataan viimeinen toimenpide
    (swap! kokonaishintaiset/tila assoc :avattu-toimenpide (-> @kohteet-kartalla/aktiivinen-nakyma
                                                                      :tila
                                                                      :gridissa-olevat-kohteen-tiedot
                                                                      last))
    (is (= (count @kanavaurakka/kanavakohteet)
           (count @kohteet-kartalla/naytettavat-kanavakohteet))
        (str "Kartalla pitäisi olla yhtä monta näytettävää kohdetta, kuin mitä kohteita on yhteensä"
             "\nKanavakohteet: " @kanavaurakka/kanavakohteet
             "\nNaytettavat kanavakohteet: " @kohteet-kartalla/naytettavat-kanavakohteet))
    (doseq [kohde-kartalla @kohteet-kartalla/kohteet-kartalla]
      (is (contains? kohde-kartalla :on-item-click) "Klikkausfunktio pitäisi löytyä")
      (is (= false (:nayta-paneelissa? kohde-kartalla)) "Kartan infopaneelissa ei tarvi näyttää mitään, koska sitä ei näytetä")
      (is (= false (:avaa-paneeli? kohde-kartalla)) "Infopaneelia ei pitäisi aukaista"))))