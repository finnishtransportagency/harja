(ns harja.tiedot.tieluvat.tieluvat-kartalla
  (:require [harja.domain.tielupa :as tielupa]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.tieluvat.tieluvat :as tiedot]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce karttataso-tieluvat (atom false))

(defn hajota-tieluvat
  "Koska yhteen tielupaan voi kuulua monta sijaintia, pitää yksi lupa hajoittaa moneksi objektiksi
  kartalle piirrettäessa."
  [luvat]
  (mapcat
    (fn [tielupa]
      ;; Yhdellä tieluvalla on monta sijaintitietoa
      ;; Yksi sijaintitieto sisältää tr-osoitetiedot, ja ::geometria avaimen takana geometriatiedot
      ;; Kartalle piirrettäessa "rikotaan osiin" jokainen tielupa, eli lisätään :sijainti-avain,
      ;; jonka taakse laitetaan geometriatieto. Yhdestä tieluvasta tulee siis monta "objektia".
      ;; Valitun asian korostaminen toimii suoraan, koska se tehdään id:n perusteella,
      ;; eli jos sama id on monella kartalla olevalla asialla, korostetaan ne kaikki.
      ;; Infopaneeliin tämä vaikuttaa enemmän, koska emme halua, että saman tieluvan tiedot
      ;; toistuvat (jos esim sijaintietoja on korkealla zoom-tasolla päällekkäin), tai että monta riviä
      ;; avataan samaan aikaan (jos se tehdään id:n perusteella). Paneelin kautta ilmoitusta
      ;; avattaessa pitää varmistaa, että aukaistaan varmasti "oikea ilmoitus", eikä jotain
      ;; väliaikaista objektia, joka sisältää vain osan tiedoista.
      (keep
        (fn [sijainti]
          (when-let [geo (::tielupa/geometria sijainti)]
            (when (not-empty geo)
              (assoc tielupa :sijainti geo))))
        (::tielupa/sijainnit tielupa)))
    luvat))

(defonce tieluvat-kartalla
  (reaction
    (let [tila @tiedot/tila]
      (when @karttataso-tieluvat
        (kartalla-esitettavaan-muotoon
          (hajota-tieluvat (:haetut-tieluvat tila))
          #(= (get-in tila [:valittu-tielupa ::tielupa/id]) (::tielupa/id %))
          (comp
            (map #(assoc % :tyyppi-kartalla :tielupa))))))))
