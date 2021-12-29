#include <iostream>
#include <i3d/image3d.h>

int main(int argc, char** argv)
{
	if (argc < 2 || argc > 3)
	{
		std::cout << "Expect one arg with image filename...\n";
		std::cout << "Expect optional anything second arg for machine reports...\n";
		return 1;
	}

	i3d::Image3d<i3d::GRAY16> img(argv[1]);

	i3d::Vector3d<size_t> min(img.GetSize());
	i3d::Vector3d<size_t> max(0);

	const i3d::GRAY16* p = img.GetFirstVoxelAddr();
	for (size_t z=0; z < img.GetSizeZ(); ++z)
	for (size_t y=0; y < img.GetSizeY(); ++y)
	for (size_t x=0; x < img.GetSizeX(); ++x, ++p)
	if (*p > 0)
	{
		min.x = std::min( min.x, x );
		min.y = std::min( min.y, y );
		min.z = std::min( min.z, z );

		max.x = std::max( max.x, x );
		max.y = std::max( max.y, y );
		max.z = std::max( max.z, z );
	}

	const size_t margin = 50; //px
	min.x = std::max( min.x-margin, (size_t)0 );
	min.y = std::max( min.y-margin, (size_t)0 );
	min.z = std::max( min.z-margin, (size_t)0 );

	max.x = std::min( max.x+margin, img.GetSizeZ()-1 );
	max.y = std::min( max.y+margin, img.GetSizeY()-1 );
	max.z = std::min( max.z+margin, img.GetSizeX()-1 );

	if (argc == 2)
	{
		std::cout << "Discovered ROI: " << min << " -> " << max << ",\n";

		max -= min;
		max.x += 1;
		max.y += 1;
		max.z += 1;
		const size_t roisize = max.x * max.y * max.z;
		const size_t imgsize = img.GetImageSize();

		std::cout << "which is " << roisize << " pixels from " << imgsize << " pixels,\n";
		std::cout << "a reduction to " << (100*roisize/imgsize) << "%, that is, "
			<< (float)imgsize/(float)roisize << " times smaller\n";
	}
	else
	{
		max -= min;
		max.x += 1;
		max.y += 1;
		max.z += 1;

		std::cout << min.x << " " << min.y << " " << min.z << " "
			<< max.x << " " << max.y << " " << max.z << "\n";
	}

	return 0;
}
