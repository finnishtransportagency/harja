(ns harja.utils)

(defn compare-many
  [comps]
  (fn [xs ys]
    (if-let [result
             (first
               (drop-while
                 zero?
                 (map
                   (fn [f x y] (prn f x y) (. f (compare x y)))
                   comps xs ys)))]
      result
      0)))

(defn lajittele-monella-monella
  "Lajittelee vektorin mäppejä käyttäen useampaa avainta/vertailijaa.
  Ottaa parametrit:
  - Avaimet: Vektori avaimia, joiden avulla sortataan. Prioriteettijärjestyksessä.
  - Vertailijat: Miten avaimia verrataan. Ensimmäiseen avaimeen käytetään ensimmäistä
    vertailijaa, toiseen toista, jne. Avaimia siis pitäisi olla siis sama määrä kuin
    vertailijoita.
  - Vektori: Järjesteltävä vektori mappeja

  Esim (lajittele-monella-avaimella [:ika :nimi] [< compare] @henkilot)"
  [avaimet vertailijat vektori]
  (sort-by (apply juxt avaimet) (compare-many vertailijat) vektori))