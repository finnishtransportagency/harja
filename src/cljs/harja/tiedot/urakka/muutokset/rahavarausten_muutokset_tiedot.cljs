(ns harja.tiedot.urakka.muutokset.rahavarausten-muutokset-tiedot
  "Urakan muutosten tiedot - rahavarausten muutokset."
  (:require [taoensso.timbre :as log]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka :as u]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as urakka-tila]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))

;; Muutostyypit:
;; - Rahavarausten muutokset

;; --- Tuck-eventit ja käsittelijät ---

(defrecord MuokkaaRahavaraustenMuutoksienSyita [])
(defrecord TallennaRahavarausmuutostenSyyt [rivit])
(defrecord TallennaRahavarausmuutostenSyytEpaonnistui [vastaus])
(defrecord TallennaRahavarausmuutostenSyytOnnistui [vastaus])

(extend-protocol tuck/Event
  MuokkaaRahavaraustenMuutoksienSyita
  (process-event [_ app]
    (assoc app :rahavarausten-syyt-muokattavana? true))

  TallennaRahavarausmuutostenSyyt
  (process-event [{:keys [rivit]} app]
    (let [urakka (:urakka @urakka-tila/yleiset)]
      (tuck-apurit/post! :tallenna-rahavarausmuutosten-syyt
        {:urakka-id (:id urakka)
         :hoitokaudet @u/valitun-urakan-hoitokaudet
         :valittu-hoitokausi (:valittu-hoitokausi app)
         :rivit (map #(select-keys % [:id :syy]) rivit)}
        {:onnistui ->TallennaRahavarausmuutostenSyytOnnistui
         :epaonnistui ->TallennaRahavarausmuutostenSyytEpaonnistui})
      app))

  TallennaRahavarausmuutostenSyytEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksien muutosten syiden tallentaminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)


  TallennaRahavarausmuutostenSyytOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (t-yhteiset/vastaus-haku-onnistui app vastaus)))
