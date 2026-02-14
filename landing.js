/* High-Performance Spotlight */
const spotlight = document.getElementById("spotlight");
let mouseX = 0,
  mouseY = 0;
let spotlightX = 0,
  spotlightY = 0;

document.addEventListener("mousemove", (e) => {
  mouseX = e.clientX;
  mouseY = e.clientY;
});

// Use RequestAnimationFrame for butter-smooth tracking
function updateSpotlight() {
  const ease = 0.15;
  spotlightX += (mouseX - spotlightX) * ease;
  spotlightY += (mouseY - spotlightY) * ease;

  spotlight.style.transform = `translate(${spotlightX - 300}px, ${spotlightY - 300}px)`;
  requestAnimationFrame(updateSpotlight);
}
updateSpotlight();

/* Advanced Scroll Reveal */
const observerOptions = {
  threshold: 0.1,
  rootMargin: "0px 0px -50px 0px",
};

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("active");
    }
  });
}, observerOptions);

document
  .querySelectorAll(".feature-card")
  .forEach((el) => observer.observe(el));

/* Feedback UX: Button Ripples */
document.querySelectorAll(".btn-primary").forEach((button) => {
  button.addEventListener("click", function (e) {
    if (navigator.vibrate) navigator.vibrate(10); // Subtle haptic tap
  });
});
