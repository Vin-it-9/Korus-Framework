// src/main/resources/static/js/app.js
document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Your framework is loaded and ready!');

    // Add some interactive functionality
    const links = document.querySelectorAll('.api-link');

    links.forEach(link => {
        link.addEventListener('click', function(e) {
            if (this.href.includes('/api/')) {
                e.preventDefault();

                // Make API call and show result
                fetch(this.href)
                    .then(response => response.json())
                    .then(data => {
                        alert('API Response: ' + JSON.stringify(data, null, 2));
                    })
                    .catch(error => {
                        console.error('API Error:', error);
                        alert('Error calling API: ' + error.message);
                    });
            }
        });
    });

    // Add current time display
    function updateTime() {
        const timeElements = document.querySelectorAll('[data-live-time]');
        timeElements.forEach(element => {
            element.textContent = new Date().toLocaleString();
        });
    }

    // Update time every second
    setInterval(updateTime, 1000);
    updateTime();
});
