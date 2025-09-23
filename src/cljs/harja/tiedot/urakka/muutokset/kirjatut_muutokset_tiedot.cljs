(ns harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot
  "Urakan muutosten tiedot - kirjatut muutokset."
  (:require [harja.pvm :as pvm]
            [taoensso.timbre :as log]
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
(defrecord KopioiHoitovuodenMuutoksetTulevilleHoitovuosille [hk-alkuvuosi urakan-hoitovuodet])

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

;; Apureita tehtävien määrämuutosten ja kustannusvaikutusten kopiointiin hoitovuodelle
(defn- muunna-tehtava-ja-maara-rivit-kohdevuodelle
  "Tekee muunnoksia tehtävät-ja-määrät riveihin, jotta ne sopivat kopioitavaksi toiselle hoitovuodelle."
  [lahderivit kohdevuosi]
  (mapv (fn [rivi]
          (-> rivi
            (assoc :hoitokauden_alkuvuosi kohdevuosi)))
    lahderivit))

(defn- merkitse-rivit-poistetuksi
  "Merkitse rivit poistetuksi."
  [lahderivit]
  (mapv #(assoc % :poistettu true) lahderivit))

(defn- korvaa-vuosien-tehtavat-ja-maara-rivit
  "Korvaa kohdevuosien tehtävä- ja määrärivit kopiolla lähdevuoden riveistä.
   Järjestää rivit hoitovuoden mukaan."
  [tehtavat-ja-maarat lahdevuosi vuodet]
  (let [tjm-per-vuosi-map (group-by :hoitokauden_alkuvuosi tehtavat-ja-maarat)
        lahderivit (get tjm-per-vuosi-map lahdevuosi)]
    (if (or (empty? lahderivit) (empty? vuodet))
      tehtavat-ja-maarat
      (->> (reduce (fn [m vuosi]
                     ;; Korvaa vuoden tehtävä- ja määrärivit lähderiveillä, aseta uusi alkuvuosi
                     (assoc m vuosi (concat
                                      ;; Merkitse vanhat rivit poistetuksi ennen korvaavien rivien lisäämistä
                                      (merkitse-rivit-poistetuksi (get m vuosi))
                                      (muunna-tehtava-ja-maara-rivit-kohdevuodelle lahderivit vuosi))))
             tjm-per-vuosi-map
             vuodet)
        (sort-by first)
        (mapcat (comp vec second))
        (vec)))))

(defn- korvaa-vuosien-kustannusvaikutukset
  "Korvaa kohdevuosien kustannusvaikutukset kopiolla lähdevuoden riveistä (jos löytyy).
   Järjestää tuloksen hoitovuoden mukaan."
  [kustannusvaikutukset lahdevuosi vuodet]
  (let [kvt-per-vuosi-map (group-by :hoitokauden_alkuvuosi kustannusvaikutukset)
        lahde-rivi (some-> (get kvt-per-vuosi-map lahdevuosi) first)]
    (if (or (nil? lahde-rivi) (empty? vuodet))
      kustannusvaikutukset
      (->> (reduce (fn [m vuosi]
                     ;; Korvaa vuoden kustannusvaikutus lähderivin tiedolla, aseta uusi alkuvuosi
                     (assoc m vuosi [(assoc lahde-rivi :hoitokauden_alkuvuosi vuosi)]))
             kvt-per-vuosi-map
             vuodet)
        (sort-by first)
        (mapcat second)
        (vec)))))

(defn kopioi-hoitovuoden-muutokset-toimenpiteen-riville
  "Kopioi yhden toimenpiteen vetolaatikko-riville lähdevuoden muutosrivit kaikille tuleville vuosille."
  [rivi lahdevuosi urakan-hoitovuodet]
  ;; Haetaan urakan vuosista tulevat vuodet, eli lähtövuotta suuremmat vuodet
  (let [tulevat-vuodet (filter #(> % lahdevuosi) urakan-hoitovuodet)]
    (if (empty? tulevat-vuodet)
      rivi
      (-> rivi
        (update :tehtavat_ja_maarat #(korvaa-vuosien-tehtavat-ja-maara-rivit % lahdevuosi tulevat-vuodet))
        (update :kustannusvaikutukset #(korvaa-vuosien-kustannusvaikutukset % lahdevuosi tulevat-vuodet))))))


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
              (into []
                (keep (fn [rivi]
                        (cond
                          (and
                            (= tehtava-id (:tehtava rivi))
                            (= hk-alkuvuosi (:hoitokauden_alkuvuosi rivi)))
                          ;; Uusia vain UI:ssa olemassaolevia rivejä ei merkitä poistetuksi, ne poistetaan kokonaan UI:sta
                          ;; Tässä uudet rivit asetetaan nil:ksi ja poistetaan lopputuloksesta
                          (when (not (:uusi? rivi))
                            ;; Tallentaessa backend-logiikka suorittaa tarvittavat toimenpiteet poistetuille riveille
                            (assoc rivi :poistettu poistettu?))

                          :else rivi))
                  tehtavat-ja-maarat))))))

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
  KopioiHoitovuodenMuutoksetTulevilleHoitovuosille
  (process-event [{hk-alkuvuosi :hk-alkuvuosi urakan-hoitovuodet :urakan-hoitovuodet} app]
    (log/debug "KopioiHoitovuodenMuutoksetTulevilleHoitovuosille, hoitovuosi: " hk-alkuvuosi)
    ;; TODO: Tässä on tarkoituksena on kopioida valitun hoitokauden muutokset urakan kaikille tuleville hoitovuosille
    ;;       Eli, valitun hoitovuoden tiedot ensin etsitään [:muokattava-muutos :toimenpiteiden-tiedot] polusta
    ;;       Sieltä otetaan talteen :tehtavat_ja_maarat ja :kustannusvaikutukset
    ;;       Sitten etsitään urakan kaikki hoitokaudet, jotka ovat saatilla :toimenpiteiden-tiedot polusta
    ;;       Ja käydään ne läpi, ja päivitetään kunkin hoitokauden :tehtavat_ja_maarat ja :kustannusvaikutukset
    ;;       Jokaisen hoitokauden päivityksessä tulee huomioida, että vanhat rivit korvataan kopioiduilla riveillä
    ;;       ja jokaisen rivin kohdalle päivitetään :hoitokauden_alkuvuosi vastaamaan kunkin hoitokauden alkuvuotta
    ;;       Lopuksi päivitetään app-tila kutsumalla koosta-tehtavat-ja-maarat-pysyvaan-muutokseen ja koosta-kustannusvaikutukset-pysyvaan-muutokseen

    (let [urakan-hoitovuodet (map #(-> % first pvm/vuosi) urakan-hoitovuodet)]
      (-> app
        (update-in [:muokattava-muutos :toimenpiteiden-tiedot]
          (fn [rivit]
            (mapv #(kopioi-hoitovuoden-muutokset-toimenpiteen-riville % hk-alkuvuosi urakan-hoitovuodet) rivit)))

        ;; Yhdistä tehtavat ja määrät, sekä kustannusvaikutukset kaikista vetolaatikoista tallennusta varten
        (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)
        (koosta-kustannusvaikutukset-pysyvaan-muutokseen)))))

;; -- Pysyvät muutokset -- LOPPUU

