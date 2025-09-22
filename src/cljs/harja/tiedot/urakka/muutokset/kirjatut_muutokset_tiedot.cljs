(ns harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot
  "Urakan muutosten tiedot - kirjatut muutokset."
  (:require [taoensso.timbre :as log]
            [tuck.core :as tuck]))

;; Muutostyypit:
;; - Pysyvät muutokset
;; - Muutostyö
;;   - Erillisrahoitettu
;;   - Poikkeama tehtävä- ja määräluettelon määrästä
;; - Johto- ja hallintokorvaus

(defn luo-tai-paivita-kustannusvaikutus
  "Rakentaa / päivittää kustannusvaikutusrivin perusavaimilla."
  [{:keys [toimenpideinstanssi hoitokauden_alkuvuosi summa]} vanha-kv]
  (assoc (or vanha-kv {})
    :toimenpideinstanssi toimenpideinstanssi
    :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
    :kustannuslaji "hankintakustannukset"
    :summa summa))


;; --- Tuck-eventit ja käsittelijät ---

;; -- Pysyvät muutokset -- ALKAA
(defrecord PaivitaToimenpiteenTehtavamaarat [toimenpideinstanssi hk-alkuvuosi taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [toimenpideinstanssi hk-alkuvuosi muutos-summa])
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])
;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi
                   ;; Yhden vetolaatikon tehtävät-ja-määrät rivit valitulta hoitovuodelta
                   taulukon-rivit :taulukon-rivit} app]
    (log/debug "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)

    (as-> app app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (update-in app [:muokattava-muutos :toimenpiteiden-tiedot]
        (fn [toimenpiderivit]
          (->
            ;; Mapataan toimenpideinstanssi -> rivi, jotta dataa on helpompi käsitellä
            (into {} (map (juxt :toimenpideinstanssi identity) toimenpiderivit))
            ;; Päivitetään vain valitun toimenpideinstanssin tehtävät ja määrät, ja ainoastaan valitun hoitokauden osalta
            (update-in [toimenpideinstanssi :tehtavat_ja_maarat]
              (fn [tehtavat-ja-maarat]
                (let [;; Suodatetaan vanhat rivit pois valitulta hoitokaudelta
                      tehtavat-ja-maarat (filterv #(not= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) tehtavat-ja-maarat)
                      ;; Lisätään tilalle uudet rivit valitulta hoitokaudelta (tuplavarmistus filtteröinnillä, että mukana tulee vain valitun hoitokauden rivit)
                      tehtavat-ja-maarat (into tehtavat-ja-maarat (filterv #(= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) taulukon-rivit))]
                  ;; Palautetaan päivitetyt tehtävät ja määrät
                  tehtavat-ja-maarat)))

            ;; Palautetaan jälleen vektorina gridille
            (vals)
            (vec))))

      ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
      (assoc-in app [:muokattava-muutos :tehtavat_ja_maarat]
        (->> (map :tehtavat_ja_maarat (get-in app [:muokattava-muutos :toimenpiteiden-tiedot]))
          (flatten)
          (vec)))))

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{muutos-summa :muutos-summa
                   toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (log/debug "PaivitaToimenpiteenTavoitehinnanMuutos, summa" muutos-summa " tpi " toimenpideinstanssi "hk-alkuvuosi " hk-alkuvuosi)

    (as-> app app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (update-in app [:muokattava-muutos :toimenpiteiden-tiedot]
        (fn [toimenpiderivit]
          (->
            ;; Mapataan toimenpideinstanssi -> rivi, jotta dataa on helpompi käsitellä
            (into {} (map (juxt :toimenpideinstanssi identity) toimenpiderivit))
            ;; Päivitetään valitun toimenpideinstanssin kustannusvaikutukset
            (update toimenpideinstanssi
              (fn [rivi]
                (let [kv-map (into {} (map (juxt :hoitokauden_alkuvuosi identity)
                                        (:kustannusvaikutukset rivi)))
                      ;; Päivitetään (tai luodaan) valitun hoitokauden kustannusvaikutus
                      kv-map (update kv-map hk-alkuvuosi
                               (fn [arvo]
                                 (luo-tai-paivita-kustannusvaikutus
                                   {:summa muutos-summa
                                    :toimenpideinstanssi toimenpideinstanssi
                                    :hoitokauden_alkuvuosi hk-alkuvuosi}
                                   arvo)))]
                  (assoc rivi :kustannusvaikutukset (-> kv-map vals vec)))))
            ;; Palautetaan jälleen vektorina gridille
            (vals)
            (vec))))

      ;; Yhdistä kustannusvaikutukset kaikista vetolaatikoista tallennusta varten
      (assoc-in app [:muokattava-muutos :kustannusvaikutukset]
        (->> (map :kustannusvaikutukset (get-in app [:muokattava-muutos :toimenpiteiden-tiedot]))
          (flatten)
          (vec)))))

  ;; TODO: Jätetään myöhemmäksi, aluksi tallennus muokkaus ja muut tärkeämmät ominaisuudet.
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (log/debug "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app))

;; -- Pysyvät muutokset -- LOPPUU

