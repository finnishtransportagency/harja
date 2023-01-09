(ns harja.views.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.bootstrap :as bs]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta :as urakka-laadunseuranta]
            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.views.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeama-tiedot]
            [harja.views.urakka.laadunseuranta.sanktiot-ja-bonukset-nakyma :as sanktiot-ja-bonukset-nakyma]
            [harja.views.urakka.laadunseuranta.mobiilityokalu :as mobiilityokalu]
            [harja.views.kanavat.urakka.laadunseuranta.hairiotilanteet :as hairiotilanteet]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.laadunseuranta.siltatarkastukset :as siltatarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.urakka :as urakka]))

(defn valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :hairiotilanteet (and (istunto/ominaisuus-kaytossa? :vesivayla)
                          (urakka/kanavaurakka? urakka)
                          (oikeudet/urakat-laadunseuranta-hairiotilanteet))

    :tarkastukset (or (and (oikeudet/urakat-laadunseuranta-tarkastukset id)
                           (not (urakka/vesivaylaurakka? urakka)))
                      (and
                        (istunto/ominaisuus-kaytossa? :vesivayla)
                        (urakka/vesivaylaurakka? urakka)
                        (oikeudet/urakat-laadunseuranta-tarkastukset id)))
    :laatupoikkeamat (or (and (oikeudet/urakat-laadunseuranta-laatupoikkeamat id)
                              (not (urakka/vesivaylaurakka? urakka)))
                         (and
                           (istunto/ominaisuus-kaytossa? :vesivayla)
                           (urakka/vesivaylaurakka? urakka)
                           (oikeudet/urakat-laadunseuranta-laatupoikkeamat id)))
    :sanktiot (or (and
                    (not (urakka/vesivaylaurakka? urakka))
                    (oikeudet/urakat-laadunseuranta-sanktiot id))
                  (and
                    (istunto/ominaisuus-kaytossa? :vesivayla)
                    (urakka/vesivaylaurakka? urakka)
                    (oikeudet/urakat-laadunseuranta-sanktiot id)))
    :siltatarkastukset (and (or (= :hoito tyyppi) (= :teiden-hoito tyyppi))
                            (oikeudet/urakat-laadunseuranta-siltatarkastukset id))
    :mobiilityokalu (not (urakka/vesivaylaurakka? urakka))))

(defn laadunseuranta [ur]
  (komp/luo
    (komp/lippu urakka-laadunseuranta/laadunseurannassa?)
    (fn [{:keys [id tyyppi] :as ur}]
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        :active (nav/valittu-valilehti-atom :laadunseuranta)}

       "Tarkastukset" :tarkastukset
       (when (valilehti-mahdollinen? :tarkastukset ur)
         [tarkastukset/tarkastukset {:nakyma tyyppi
                                     :urakka ur}])

       "Laatupoikkeamat" :laatupoikkeamat
       (when (valilehti-mahdollinen? :laatupoikkeamat ur)
         [tuck/tuck tila/laatupoikkeamat laatupoikkeamat/laatupoikkeamat])

       (if @tiedot-urakka/yllapitourakka? "Sakot ja bonukset" "Sanktiot ja bonukset") :sanktiot
       (when (valilehti-mahdollinen? :sanktiot ur)
         [sanktiot-ja-bonukset-nakyma/sanktiot-ja-bonukset])

       "Siltatarkastukset" :siltatarkastukset
       (when (valilehti-mahdollinen? :siltatarkastukset ur)
         ^{:key "siltatarkastukset"}
         [siltatarkastukset/siltatarkastukset])

       "Häiriötilanteet"
       :hairiotilanteet
       (when (valilehti-mahdollinen? :hairiotilanteet ur)
         [hairiotilanteet/hairiotilanteet])

       "Mobiilityökalu" :mobiilityokalu
       ^{:key "mobiilityokalu"}
       (when (valilehti-mahdollinen? :mobiilityokalu ur)
         [mobiilityokalu/mobiilityokalu])])))
