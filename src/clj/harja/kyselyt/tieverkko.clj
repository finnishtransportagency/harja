(ns harja.kyselyt.tieverkko
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/tieverkko.sql"
            {:positional? true})

(defn hae-tr-osoite-valille-ehka
  "Hakee TR osoitteen pisteille. Jos teille ei löydy yhteistä pistettä, palauttaa nil."
  [db x1 y1 x2 y2 threshold]
  (let [rivi (first (hae-tr-osoite-valille* db x1 y1 x2 y2 threshold))]
    (and (:tie rivi)
         rivi)))

(defn hae-tr-osoite-ehka
  "Hakee TR osoitteen pisteelle, jos osoitetta ei löydy, palauttaa nil."
  [db x y threshold]
  (let [rivi (first (hae-tr-osoite* db x y threshold))]
    (and (:tie rivi)
         rivi)))

(defn hae-trpisteiden-valinen-tieto-raaka
  "Raaka tulos 'laske_tr_tiedot' SQL funktiosta"
  [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as params}]
  (map (fn [tieto]
         (update tieto :pituudet konv/jsonb->clojuremap))
       (hae-trpisteiden-valinen-tieto db params))  )

(defn laske-max-loppuetaisyys [osoitteet]
  (let [laske-loppuetaisyys (fn [{:keys [pituus tr-alkuetaisyys]}]
                              (+ pituus tr-alkuetaisyys))
        loppuetaisyydet (map laske-loppuetaisyys osoitteet)]
    (apply max (vec loppuetaisyydet))))

(defn yhdista-osoitteet [osoitteet]
  (let [jarjestys-fn (fn [osoite1 osoite2]
                       (let [k1 (:tr-kaista osoite1)
                             k2 (:tr-kaista osoite2)
                             a1 (:tr-alkuetaisyys osoite1)
                             a2 (:tr-alkuetaisyys osoite2)]
                         (if (= k1 k2)
                           (compare a1 a2)
                           (compare k1 k2))))
        osoitteet-jarjestyksessa (sort jarjestys-fn osoitteet)
        yhdista-jos-mahdollista (fn [t osoite]
                                  (if-let [v (last t)]
                                    (if (and (= (:tr-kaista v) (:tr-kaista osoite))
                                             (= (:tr-alkuetaisyys osoite)
                                                (+ (:tr-alkuetaisyys v) (:pituus v))))
                                        (conj (pop t) (update v :pituus + (:pituus osoite)))
                                        (conj t osoite))
                                    [osoite]))]
    (reduce yhdista-jos-mahdollista nil osoitteet-jarjestyksessa)))

(defn muodosta-ajoradat
  "Löydä osiot, ja yhdistä 'continuous' kaistat.

   Inputista:    [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}...],

   tekee output: [{:osiot [{:pituus 1500,
                            :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                            :tr-alkuetaisyys 0}],
                   :tr-ajorata 1}...]"
  [osoitteet]
  (let [osoitteet-by-ajoradat (group-by :tr-ajorata osoitteet)
        muodosta-ajorata (fn [[ajorata osoitteet]]
                           (let [yhdistetty-osoitteet (yhdista-osoitteet osoitteet)
                                 osiotteet-jarjestyksessa (sort-by :tr-alkuetaisyys yhdistetty-osoitteet)
                                 osiot-etsija (fn [t osoite]
                                                (if-let [viimeinen-osio (peek t)]
                                                  (if (< (laske-max-loppuetaisyys viimeinen-osio) (:tr-alkuetaisyys osoite))
                                                    (conj t [osoite])
                                                    (let [a 1]
                                                      (conj (pop t) (conj viimeinen-osio osoite))))
                                                  [[osoite]]))
                                 osiot (reduce osiot-etsija nil osiotteet-jarjestyksessa)
                                 muodosta-osiot (fn [osion-osoitteet]
                                                  (let [alkuetaisyys (:tr-alkuetaisyys (first osion-osoitteet))
                                                        pituus (- (laske-max-loppuetaisyys osion-osoitteet) alkuetaisyys)
                                                        muodosta-kaistat (fn [{:keys [pituus tr-kaista tr-alkuetaisyys]}]
                                                                           {:pituus pituus :tr-kaista tr-kaista :tr-alkuetaisyys tr-alkuetaisyys})
                                                        kaistat (map muodosta-kaistat osion-osoitteet)]
                                                    {:pituus pituus
                                                     :kaistat (vec kaistat)
                                                     :tr-alkuetaisyys alkuetaisyys}))]
                             {:osiot (vec (map muodosta-osiot osiot))
                              :tr-ajorata ajorata}))]
    (vec (map muodosta-ajorata osoitteet-by-ajoradat))))

(defn hae-trpisteiden-valinen-tieto-yhdistaa
  [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as params}]
  (let [raaka-osat (hae-trpisteiden-valinen-tieto-raaka db params)
        kasitele-raaka (fn [raaka-osa]
                         (let [pituudet (:pituudet raaka-osa)
                               pituudet-ajoradat (assoc pituudet :ajoradat (muodosta-ajoradat (:osoitteet pituudet)))
                               pituudet-lopputulos (dissoc pituudet-ajoradat :osoitteet)]
                           (assoc raaka-osa :pituudet pituudet-lopputulos)))]
    (map kasitele-raaka raaka-osat)))

(defn onko-tierekisteriosoite-validi? [db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (some? (tierekisteriosoite-viivaksi db osoite))))

(defn ovatko-tierekisteriosoitteen-etaisyydet-validit? [db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (onko-osoitteen-etaisyydet-validit? db osoite)))

(defn laske-tien-osien-pituudet
  "Pätkitään funkkari osiin, jotta se on helpommin testattavissa. Tämä laskee siis
  tien pätkälle pituudet riippuen siitä, miten osan-pituudet listassa on annettu"
  [osan-pituudet kohde]
  (let [varakohde kohde
        kohde (if (and (not (nil? (:aosa kohde))) (not (nil? (:losa kohde)))
                    (or (> (:aosa kohde) (:losa kohde))
                      (and (= (:aosa kohde) (:losa kohde))
                        (> (:aet kohde) (:let kohde)))))
                (-> kohde
                  (assoc :aosa (:losa varakohde))
                  (assoc :losa (:aosa varakohde))
                  (assoc :aet (:let varakohde))
                  (assoc :let (:aet varakohde)))
                kohde)]
    ;; Pieni validointi kohteen arvoille
    (when (and (not (nil? (:aosa kohde))) (not (nil? (:losa kohde)))
            (<= (:aosa kohde) (:losa kohde)))
      (reduce (fn [k rivi]
                (let [tulos
                      (cond
                        ;; Kun alkuosa ja loppuosa ovat erit
                        ;; Alkuosa täsmää, joten ei oteta koko pituutta, vaan pelkästään jäljelle jäävä pituus
                        (and (not= (:aosa k) (:losa k))
                          (= (:aosa k) (:osa rivi)))
                        (if (:ajoratojen-pituus rivi)
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             ;; Ota ensin pääpituudesta kaikki jäljelle jäävä, jos alkuetäisyys osuu 0 ajoradan pituuden sisään
                                             (if (< (:aet k) (:pituus rivi))
                                               (+
                                                 (- (:pituus rivi) (:aet k)) ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                                 (* (:ajoratojen-pituus rivi) (:ajoratojen-maara rivi)) ;; Ja lisätään muiden ajoratojen pituus
                                                 )
                                               ;; Jos se ei osu yhdelle ajoradalle esim tilanteessa jossa ajoratoja on 0,1,2 ja 0:n pituus on 1000 ja 1,2 = 100
                                               ;; Niin otetaan loput ajoradoista, koska kaikkea ei saada 0 ajoradasta
                                               (* (- (:ajoratojen-pituus rivi) (- (:aet k) (:pituus rivi))) (:ajoratojen-maara rivi)))))
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             (- (:pituus rivi) (:aet k)) ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                             )))

                        ;; Kun alkuosa ja loppuosa ovat erit
                        ;; Jos loppuosa täsmää osalistan osaan, niin otetaan vain loppuosan etäisyys
                        (and (not= (:aosa k) (:losa k))
                          (= (:losa k) (:osa rivi)))
                        (if (:ajoratojen-pituus rivi)
                          ;; Laskennat useammalle ajoradalle
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             ;; Tarkistetaan osuuko vaadittu pituus ensimmäiselle ajoradalle
                                             (if (< (:let k) (:pituus rivi))
                                               ;; Osuu, joten otetaan sen verran kuin halutaan, ei koko osan pituutta
                                               (:let k)
                                               ;; Jos ei osu, niin otetaan koko pituus + ajoratojen pituus siltä osalta, kun tarvitaan
                                               (+ (:pituus rivi) ;; Ensimmäiosen ajoradan pituus
                                                 (*
                                                   (- (:let k) (:pituus rivi)) ;;Jäljelle jäävä määrä
                                                   (:ajoratojen-maara rivi)) ;; Kerrotaan ajoratojen jäljelle jäävä pituus ajoratojen määrällä
                                                 ))))

                          ;; Laskennat yhdelle ajoradalle
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             (:let k)       ;; Lopposan pituus, eli alusta tähän asti, ei siis koko osan pituutta
                                             )))
                        ;; Kun alkuosa on sama kuin loppuosa
                        ;; Ja osa on olemassa. Eli jos tiepätkään ei kuulu se osa mitä mitataan, niin ei myöskään
                        ;; lasketa sitä mukaan
                        ;; Otetaan vain osien väliin jäävä pätkä mukaan
                        (and (= (:osa rivi) (:aosa k))
                          (= (:aosa k) (:losa k)))
                        (if
                          ;; Tarkistetaan, että lasketaanko ajoradan pituuksia vai pelkän osan pituuksia
                          (:ajoratojen-pituus rivi)
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             (-
                                               ;; Loppuetäisyys ei voi olla pidempi, kuin koko rivin pituus
                                               (if (> (:let k) (:pituus rivi))
                                                 (:pituus rivi)
                                                 (:let k))
                                               (if          ;; Varmistetaan, että alkuetäisyys ei ole suurempi kuin pituus
                                                 (> (:aet k) (:pituus rivi))
                                                 0
                                                 (:aet k))
                                               )            ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                             ;; Ja lisätään ajoratojen pituus, jos vaadittu let on suurempi, kuin ensimmäisen osan pituus ja aet on pienempi
                                             (if (and (> (:let k) (:pituus rivi)) (< (:aet k) (:pituus rivi)))
                                               (*           ;; Kerrotaan ensimmäisen ajoradan ylittävä osuus ajoratojen määrällä ja huomioidaan, että ei voida ottaa liian pitkää pätkää matkaan
                                                 (- (min (:let k) (+ (:pituus rivi) (:ajoratojen-pituus rivi)))
                                                   (:pituus rivi))
                                                 (:ajoratojen-maara rivi))
                                               0)
                                             ;; Jos sekä aet että let on suurempia, kuin ensimmäisen osan pituus
                                             (if (and (> (:let k) (:pituus rivi)) (> (:aet k) (:pituus rivi)))
                                               (cond
                                                 ;; Kun sekä alkuetäisyys, että loppuetäisyys on pienempiä, kuin ajoradan pituus
                                                 (and (< (:aet k) (:ajoratojen-pituus rivi)) (< (:let k) (:ajoratojen-pituus rivi)))
                                                 (* (:ajoratojen-maara rivi) (- (:let k) (:aet k)))
                                                 ;; Kun loppuetäisyys on suurempi, kuin ajoratojen pituus (virhetilanne)
                                                 (and (< (:aet k) (:ajoratojen-pituus rivi)) (> (:let k) (:ajoratojen-pituus rivi)))
                                                 (* (:ajoratojen-maara rivi) (- (:ajoratojen-pituus rivi) (:aet k)))
                                                 ;; Jossakin virhetilanteessa mikään pituus ei täsmää, niin annetaan nolla
                                                 :else 0)
                                               0)))
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             (-
                                               ;; Loppuetäisyys ei voi olla pidempi, kuin koko rivin pituus
                                               (if (> (:let k) (:pituus rivi))
                                                 (:pituus rivi)
                                                 (:let k))
                                               (:aet k))    ;; Osamäpin osan pituudesta vähennetään alkuosan etäisyys
                                             )))

                        ;; alkuosa tai loppuosa ei täsmää, joten otetaan koko osan pituus
                        :else
                        (if
                          ;; Varmistetaan, että tietokannasta on saatu validi osa
                          ;; Ja että tietokannasta saatu osa pitää käsitellä vielä tälle kohteelle.
                          ;; Jos :osa on suurimpi kuin :losa, niin mitään käsittelyitä ei tarvita enää
                          ;; Ja jos :osa on pienempi kuin :aosa, niin käsittelyitä ei tarvita
                          (and (:pituus rivi)
                            (< (:osa rivi) (:losa k))
                            (> (:osa rivi) (:aosa k)))
                          (assoc k :pituus (+
                                             (:pituus k)    ;; Nykyinen pituus
                                             (:pituus rivi) ;; Osamäpin osan pituus
                                             ))
                          k))]
                  tulos))
        ;; Annetaan reducelle mäppi, jossa :pituus avaimeen lasketaan annetun tien kohdan pituus.
        {:pituus 0 :aosa (:aosa kohde) :aet (:aet kohde) :losa (:losa kohde) :let (:let kohde) :tie (:tie kohde)}
        osan-pituudet))))

(defn laske-tierekisteriosoitteen-pituus [db tierekisteriosoite]
  (let [;; Jos osan hae-osien-pituudet kyselyn tulos muuttuu, tämän funktion toiminta loppuu
        ;; Alla oleva reduce olettaa, että sille annetaan osien pituudet desc järjestyksessä ja muodossa
        ;; ({:osa 1 :pituus 3000} {:osa 2 :pituus 3000})
        ;; Joten jos tieosoite annetaan nurinpäin, niin muokataan se sopivaan muotoon
        varatierekisteriosoite tierekisteriosoite
        tierekisteriosoite (if (and (not (nil? (:aosa tierekisteriosoite))) (not (nil? (:losa tierekisteriosoite)))
                                 (> (:aosa tierekisteriosoite) (:losa tierekisteriosoite)))
                             (-> tierekisteriosoite
                               (assoc :aosa (:losa varatierekisteriosoite))
                               (assoc :aet (:let varatierekisteriosoite))
                               (assoc :losa (:aosa varatierekisteriosoite))
                               (assoc :let (:aet varatierekisteriosoite)))
                             tierekisteriosoite)
        osien-pituudet (hae-osien-pituudet db {:tie (:tie tierekisteriosoite)
                                               :aosa (:aosa tierekisteriosoite)
                                               :losa (:losa tierekisteriosoite)})
        pituus (laske-tien-osien-pituudet osien-pituudet tierekisteriosoite)]
    pituus))
