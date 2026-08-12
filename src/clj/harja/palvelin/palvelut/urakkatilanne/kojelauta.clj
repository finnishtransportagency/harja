(ns harja.palvelin.palvelut.urakkatilanne.kojelauta
  (:require [clojure.set :as set]
            [com.stuartsierra.component :as component]

            [harja.kyselyt.kojelauta :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as v]
            [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))


(defn- liita-indeksikertoimet
  "Liitetään urakka- ja hoitovuosikohtaiseen riviin ko. vuoden indeksikerroin, jos saatavilla.
  Tätä tietoa voidaan hyödyntää kojelautanäkymässä päättelemään, onko kustannussuunnitelmien vahvistaminen pahasti myöhässä,
  vai esimerkiksi indeksitietojen puuttumisen vuoksi ei vielä edes mahdollista."
  [db kayttaja {:keys [hoitokauden_alkuvuosi id] :as rivi}]
  (let [urakan-vuoden-indeksikerroin (:indeksikerroin
                                       (first
                                         (filter
                                           #(= hoitokauden_alkuvuosi (:vuosi %))
                                           (budjettisuunnittelu/hae-urakan-indeksikertoimet db kayttaja {:urakka-id id}))))]
    (assoc rivi :indeksikerroin urakan-vuoden-indeksikerroin)))


(defn hae-urakat-kojelautaan
  [db kayttaja {:keys [urakkatyyppi hoitokauden-alkuvuosi urakka-idt evk-idt] :as hakuehdot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakkatilanne kayttaja)
  (let [params {:hoitokauden_alkuvuosi hoitokauden-alkuvuosi
                :urakat_annettu (boolean (seq urakka-idt))
                :urakka_idt urakka-idt
                :evkt_annettu (boolean (seq evk-idt))
                :evk_idt evk-idt}

        urakat (cond
                 ;; tässä kohti hoito = MHU, vanhoja alueurakoita ei tueta
                 (= urakkatyyppi :hoito)
                 (into []
                   (mapv
                     (comp
                       (fn [ks-tilat]
                         (update ks-tilat :ks_tila konv/jsonb->clojuremap))
                       #(liita-indeksikertoimet db kayttaja %))
                     (q/hae-hoidon-urakat-kojelautaan db params)))

                 (= urakkatyyppi :paallystys)
                 (mapv (fn [rivi]
                         (-> rivi
                           (update :virheelliset_kohteet
                             (fn [kohteet]
                               (mapv
                                 #(konv/pgobject->map %
                                    :id :long
                                    :kohdenumero :string
                                    :tunnus :string
                                    :kohdenimi :string
                                    :lahetysvirhe :string)
                                 (konv/pgarray->vector kohteet))))))
                   (q/hae-paallystysurakat-kojelautaan db (set/rename-keys params {:hoitokauden_alkuvuosi :vuosi}))))

        ;; Lisätään vielä välikatselmuksen päätöksien määrä
        urakat (if (= urakkatyyppi :hoito)
                 (mapv
                   (fn [urakka]
                     (let [paatokset
                           (v/palauta-kaikki-mahdolliset-ja-tehdyt-paatokset-kojelautaan
                             db kayttaja
                             {:urakkaid (:id urakka)
                              :kuluva-hoitovuosi hoitokauden-alkuvuosi})]

                       (assoc urakka
                         :tehdyt-paatokset-count
                         (count (:tietokanta-paatokset paatokset))

                         :mahdolliset-paatokset-count
                         (count (:mahdolliset-paatokset paatokset)))))
                   urakat)
                 ;; Ylläpitourakoille ei haeta välikatselmuksen päätöksiä
                 urakat)]
    urakat))


(defrecord KojelautaHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakat-kojelautaan
      (fn [kayttaja hakuehdot]
        (hae-urakat-kojelautaan db kayttaja hakuehdot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :hae-urakat-kojelautaan)
    this))
