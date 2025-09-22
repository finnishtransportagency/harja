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


;; --- Tuck-eventit ja käsittelijät ---

;; -- Pysyvät muutokset -- ALKAA
(defrecord PaivitaToimenpiteenTehtavamaarat [toimenpideinstanssi hk-alkuvuosi taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [toimenpideinstanssi hk-alkuvuosi muutos-summa])
(defrecord MerkitseTehtavanMaaramuutosPoistetuksi [toimenpideinstanssi tehtava-id hk-alkuvuosi poistettu?])
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])

(defn muokkaa-toimenpiteen-rivit-pysyva-muutos
  "Palauttaa app-tilan, jossa yhden toimenpideinstanssin vetolaatikon rivejä on muokattu muokkaus-fn avulla."
  [app toimenpideinstanssi muokkaus-fn]
  (update-in app [:muokattava-muutos :toimenpiteiden-tiedot]
    (fn [rivit]
      (mapv (fn [rivi]
              (if (= toimenpideinstanssi (:toimenpideinstanssi rivi))
                (muokkaus-fn rivi)
                rivi))
        rivit))))

(defn koosta-tehtavat-ja-maarat-pysyvaan-muutokseen
  "Koostetaan tehtävien määrämuutokset kaikista toimenpide-vetolaatikoista yhteen vektoriksi tallennusta varten."
  [app]
  ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
  (assoc-in app [:muokattava-muutos :tehtavat_ja_maarat]
    (->> (map :tehtavat_ja_maarat (get-in app [:muokattava-muutos :toimenpiteiden-tiedot]))
      (flatten)
      (vec))))

(defn luo-tai-paivita-kustannusvaikutus
  "Rakentaa / päivittää kustannusvaikutusrivin perusavaimilla."
  [{:keys [toimenpideinstanssi hoitokauden_alkuvuosi summa]} vanha-kv]
  (assoc (or vanha-kv {})
    :toimenpideinstanssi toimenpideinstanssi
    :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
    :kustannuslaji "hankintakustannukset"
    :summa summa))

(defn koosta-kustannusvaikutukset-pysyvaan-muutokseen
  "Koostetaan kustannusvaikutukset kaikista toimenpide-vetolaatikoista yhteen vektoriksi tallennusta varten."
  [app]
  ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
  (assoc-in app [:muokattava-muutos :kustannusvaikutukset]
    (->> (map :kustannusvaikutukset (get-in app [:muokattava-muutos :toimenpiteiden-tiedot]))
      (flatten)
      (vec))))

;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi
                   ;; Yhden vetolaatikon tehtävät-ja-määrät rivit valitulta hoitovuodelta
                   taulukon-rivit :taulukon-rivit} app]
    (log/debug "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)

    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään vain valitun toimenpideinstanssin tehtävät ja määrät, ja ainoastaan valitun hoitokauden osalta
          (update rivi :tehtavat_ja_maarat
            (fn [tehtavat-ja-maarat]
              (let [;; Suodatetaan vanhat rivit pois valitulta hoitokaudelta
                    tehtavat-ja-maarat (filterv #(not= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) tehtavat-ja-maarat)
                    ;; Lisätään tilalle uudet rivit valitulta hoitokaudelta (tuplavarmistus filtteröinnillä, että mukana tulee vain valitun hoitokauden rivit)
                    tehtavat-ja-maarat (into tehtavat-ja-maarat (filterv #(= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) taulukon-rivit))]
                ;; Palautetaan päivitetyt tehtävät ja määrät
                tehtavat-ja-maarat)))))

      ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
      (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)))

  MerkitseTehtavanMaaramuutosPoistetuksi
  (process-event [{toimenpideinstanssi :toimenpideinstanssi
                   tehtava-id :tehtava-id
                   hk-alkuvuosi :hk-alkuvuosi
                   poistettu? :poistettu?} app]
    (log/debug "MerkitseTehtavanMaaramuutosPoistetuksi, tpi " toimenpideinstanssi " tehtava-id " tehtava-id " hk-alkuvuosi " hk-alkuvuosi " poistettu? " poistettu?)

    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään vain valitun toimenpideinstanssin tehtävät ja määrät, ja ainoastaan valitun hoitokauden osalta

          (update rivi :tehtavat_ja_maarat
            (fn [tehtavat-ja-maarat]
              (mapv (fn [rivi]
                      (if (and (= tehtava-id (:tehtava rivi))
                            (= hk-alkuvuosi (:hoitokauden_alkuvuosi rivi)))
                        ;; Kun rivi merkitään poistetuksi tällä avaimella, grid ymmärtää piilottaa sen
                        ;; Tallentaessa backend-logiikka osaa suorittaa tarvittavat toimenpiteet poistetuille riveille
                        (assoc rivi :poistettu poistettu?)
                        rivi))
                tehtavat-ja-maarat)))))

      ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
      (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)))

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{muutos-summa :muutos-summa
                   toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (log/debug "PaivitaToimenpiteenTavoitehinnanMuutos, summa" muutos-summa " tpi " toimenpideinstanssi "hk-alkuvuosi " hk-alkuvuosi)

    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään valitun toimenpideinstanssin kustannusvaikutukset
          (update rivi :kustannusvaikutukset
            (fn [kustannusvaikutukset]
              (let [kv-map (into {} (map (juxt :hoitokauden_alkuvuosi identity)
                                      kustannusvaikutukset))
                    ;; Päivitetään (tai luodaan) valitun hoitokauden kustannusvaikutus
                    kv-map (update kv-map hk-alkuvuosi
                             (fn [arvo]
                               (luo-tai-paivita-kustannusvaikutus
                                 {:summa muutos-summa
                                  :toimenpideinstanssi toimenpideinstanssi
                                  :hoitokauden_alkuvuosi hk-alkuvuosi}
                                 arvo)))]
                (-> kv-map vals vec))))))

      ;; Yhdistä kustannusvaikutukset kaikista vetolaatikoista tallennusta varten
      (koosta-kustannusvaikutukset-pysyvaan-muutokseen)))

  ;; TODO: Jätetään myöhemmäksi, aluksi tallennus muokkaus ja muut tärkeämmät ominaisuudet.
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (log/debug "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app))

;; -- Pysyvät muutokset -- LOPPUU

